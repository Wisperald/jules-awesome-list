#!/usr/bin/env bash
#
# Type-checks the TDLib-facing code against TDLib's real API, without needing
# the Android SDK, the NDK, or a built TDLib.
#
# How: TDLib's authoritative scheme (td/generate/scheme/td_api.tl) is turned into
# a TdApi.java by tools/generate_tdapi.py, compiled together with TDLib's own
# Client.java, and the app's td/ + data/ + pure util/ sources are compiled against
# the result with a stand-alone Kotlin compiler. The unit tests are run too.
#
# This catches exactly the class of bug that is invisible until an Android build:
# a TdApi class or field that was renamed, moved, or never existed.
#
# Requirements: JDK 17+, python3, git, curl. Everything else is downloaded into
# build/api-verification/ on first run.
#
# Usage: tools/verify-tdlib-api.sh [tdlib-git-revision]
set -euo pipefail

REVISION="${1:-master}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$ROOT/build/api-verification"
DEPS="$WORK/deps"
KOTLIN_VERSION="1.9.24"
COROUTINES_VERSION="1.8.1"
CENTRAL="https://repo1.maven.org/maven2"

mkdir -p "$DEPS" "$WORK/tdlib/org/drinkless/tdlib" "$WORK/stubs/android/util" "$WORK/stubs/android/content"

fetch() { # fetch <url> <target>
    [ -s "$2" ] || curl -fsS --max-time 900 -o "$2" "$1"
}

echo "==> TDLib scheme ($REVISION)"
if [ ! -d "$WORK/td/.git" ]; then
    git clone --depth 1 --filter=blob:none --sparse https://github.com/tdlib/td.git "$WORK/td"
    # Cone-mode sparse checkout takes directories only; the version is read
    # straight out of the object store instead of being checked out.
    git -C "$WORK/td" sparse-checkout set td/generate/scheme example/java/org/drinkless/tdlib
fi
git -C "$WORK/td" fetch --depth 1 origin "$REVISION" >/dev/null 2>&1 || true
git -C "$WORK/td" checkout -q FETCH_HEAD 2>/dev/null || true
TD_VERSION="$(git -C "$WORK/td" show HEAD:CMakeLists.txt | sed -n 's/.*project(TDLib VERSION \([0-9.]*\).*/\1/p' | head -1)"
echo "    TDLib $TD_VERSION at $(git -C "$WORK/td" rev-parse --short HEAD)"

echo "==> Generating TdApi.java from td_api.tl"
python3 "$ROOT/tools/generate_tdapi.py" \
    "$WORK/td/td/generate/scheme/td_api.tl" \
    "$WORK/tdlib/org/drinkless/tdlib/TdApi.java"
cp "$WORK/td/example/java/org/drinkless/tdlib/Client.java" "$WORK/tdlib/org/drinkless/tdlib/"

echo "==> Android stubs"
cat > "$WORK/stubs/android/util/Log.java" <<'EOF'
package android.util;

public final class Log {
    public static int v(String tag, String msg) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
    public static int e(String tag, String msg) { return 0; }
    public static int e(String tag, String msg, Throwable tr) { return 0; }
}
EOF
cat > "$WORK/stubs/android/content/SharedPreferences.java" <<'EOF'
package android.content;

public interface SharedPreferences {
    long getLong(String key, long defValue);
    boolean getBoolean(String key, boolean defValue);
    String getString(String key, String defValue);
    Editor edit();

    interface Editor {
        Editor putLong(String key, long value);
        Editor putBoolean(String key, boolean value);
        Editor putString(String key, String value);
        Editor clear();
        void apply();
        boolean commit();
    }
}
EOF
cat > "$WORK/stubs/android/content/Context.java" <<'EOF'
package android.content;

import java.io.File;

public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public abstract Context getApplicationContext();
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
    public abstract File getFilesDir();
    public abstract File getExternalFilesDir(String type);
}
EOF

echo "==> Dependencies"
fetch "$CENTRAL/org/jetbrains/kotlin/kotlin-compiler-embeddable/$KOTLIN_VERSION/kotlin-compiler-embeddable-$KOTLIN_VERSION.jar" "$DEPS/kotlin-compiler-embeddable.jar"
fetch "$CENTRAL/org/jetbrains/kotlin/kotlin-stdlib/$KOTLIN_VERSION/kotlin-stdlib-$KOTLIN_VERSION.jar" "$DEPS/kotlin-stdlib.jar"
fetch "$CENTRAL/org/jetbrains/kotlin/kotlin-daemon-embeddable/$KOTLIN_VERSION/kotlin-daemon-embeddable-$KOTLIN_VERSION.jar" "$DEPS/kotlin-daemon-embeddable.jar"
fetch "$CENTRAL/org/jetbrains/kotlin/kotlin-script-runtime/$KOTLIN_VERSION/kotlin-script-runtime-$KOTLIN_VERSION.jar" "$DEPS/kotlin-script-runtime.jar"
fetch "$CENTRAL/org/jetbrains/annotations/13.0/annotations-13.0.jar" "$DEPS/annotations.jar"
fetch "$CENTRAL/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar" "$DEPS/trove4j.jar"
fetch "$CENTRAL/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/$COROUTINES_VERSION/kotlinx-coroutines-core-jvm-$COROUTINES_VERSION.jar" "$DEPS/coroutines.jar"
fetch "$CENTRAL/junit/junit/4.13.2/junit-4.13.2.jar" "$DEPS/junit.jar"
fetch "$CENTRAL/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar" "$DEPS/hamcrest.jar"

KOTLINC_CP="$DEPS/kotlin-compiler-embeddable.jar:$DEPS/kotlin-stdlib.jar:$DEPS/kotlin-daemon-embeddable.jar:$DEPS/kotlin-script-runtime.jar:$DEPS/annotations.jar:$DEPS/trove4j.jar"
kotlinc() { java -Xmx3g -cp "$KOTLINC_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"; }

echo "==> Compiling TdApi, Client and the Android stubs"
rm -rf "$WORK/classes" && mkdir -p "$WORK/classes"
javac -nowarn -J-Xmx3g -d "$WORK/classes" \
    "$WORK/tdlib/org/drinkless/tdlib"/*.java \
    $(find "$WORK/stubs" -name '*.java')

MAIN="$ROOT/app/src/main/java"
SOURCES=$(find "$MAIN/com/tvgram/td" "$MAIN/com/tvgram/data" -name '*.kt')
SOURCES="$SOURCES $MAIN/com/tvgram/util/Formatting.kt $MAIN/com/tvgram/util/Initials.kt"

echo "==> Compiling the TDLib-facing Kotlin against the real API"
rm -rf "$WORK/out"
kotlinc -no-stdlib -jvm-target 17 \
    -classpath "$WORK/classes:$DEPS/kotlin-stdlib.jar:$DEPS/coroutines.jar:$DEPS/annotations.jar" \
    -d "$WORK/out" $SOURCES

echo "==> Compiling and running the unit tests"
rm -rf "$WORK/test-out"
kotlinc -no-stdlib -jvm-target 17 \
    -classpath "$WORK/out:$WORK/classes:$DEPS/kotlin-stdlib.jar:$DEPS/junit.jar:$DEPS/hamcrest.jar" \
    -d "$WORK/test-out" $(find "$ROOT/app/src/test/java" -name '*.kt')

RUNTIME_CP="$WORK/test-out:$WORK/out:$WORK/classes:$DEPS/kotlin-stdlib.jar:$DEPS/junit.jar:$DEPS/hamcrest.jar"
SUITES=$(cd "$ROOT/app/src/test/java" && find . -name '*Test.kt' \
    | sed 's|^\./||; s|\.kt$||; s|/|.|g')
java -cp "$RUNTIME_CP" org.junit.runner.JUnitCore $SUITES

echo
echo "OK — verified against TDLib $TD_VERSION"
