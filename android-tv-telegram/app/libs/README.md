# TDLib artifacts go here

This directory is intentionally empty in git. The build expects **one** of the following
layouts before it will compile:

## Option A — a prebuilt AAR

```
app/libs/tdlib.aar
```

The AAR must contain the `org.drinkless.tdlib` Java classes *and* `libtdjni.so` for every
ABI you intend to run on.

## Option B — the generated Java sources plus the native libraries
*(the shortest path after building TDLib yourself)*

```
app/libs/java/org/drinkless/tdlib/Client.java
app/libs/java/org/drinkless/tdlib/TdApi.java
app/libs/jniLibs/arm64-v8a/libtdjni.so
...
```

`app/build.gradle.kts` registers `libs/java` as an extra Java source directory, so the
bindings are compiled together with the app — no jar packaging step.

## Option C — a jar plus the native libraries

```
app/libs/tdlib.jar
app/libs/jniLibs/arm64-v8a/libtdjni.so
app/libs/jniLibs/armeabi-v7a/libtdjni.so
app/libs/jniLibs/x86_64/libtdjni.so
app/libs/jniLibs/x86/libtdjni.so
```

`app/build.gradle.kts` adds `libs/jniLibs` as a JNI source directory, so the `.so` files
are packaged into the APK automatically.

## Which ABIs do I actually need?

| Device | ABI |
| --- | --- |
| Most Android TV boxes, Google TV, Chromecast | `arm64-v8a` (older ones: `armeabi-v7a`) |
| NVIDIA Shield | `arm64-v8a` |
| Android TV emulator images | `x86_64` |

Shipping only `arm64-v8a` and `x86_64` keeps the APK roughly half the size and covers
everything sold in the last several years.

## Version requirement

**TDLib 1.8.67** is the version this code is verified against — run
`tools/verify-tdlib-api.sh` to re-verify against any other revision.

The client uses the `org.drinkless.tdlib` package (not the older
`org.drinkless.td.libcore.telegram`), the generic `TdApi.Function<R>` signature, the flat
`SetTdlibParameters` request, `TdApi.User.usernames`, and `DraftMessage.content`
(`DraftMessageContent`, which replaced the older `input_message_text` field). An older
1.8.x build will fail to compile on that last one; the verification script names the
mismatch precisely.

See `../../docs/BUILDING_TDLIB.md` for the build recipe.
