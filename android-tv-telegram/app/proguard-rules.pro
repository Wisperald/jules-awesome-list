# TDLib is reached from JNI in both directions: keep the generated API surface.
-keep class org.drinkless.tdlib.** { *; }
-keep class org.drinkless.td.libcore.telegram.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Compose keeps what it needs through its own rules; nothing extra required.
