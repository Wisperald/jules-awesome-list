// AGP 9 compiles Kotlin itself (built-in Kotlin). The explicit KGP classpath entry
// pins the Kotlin compiler to the same version as the Compose compiler plugin.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
