# Project-specific R8 rules.
# Manifest components (activities, services, receivers, widget providers) are kept
# automatically by AAPT-generated rules; AndroidX libraries ship their own consumer rules.
# The app uses no reflection-based serialization, so no extra keep rules are required.

# Strip verbose/debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
