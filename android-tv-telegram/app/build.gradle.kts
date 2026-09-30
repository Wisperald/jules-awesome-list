import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Telegram credentials are resolved, in order of precedence, from:
 *   1. environment variables TELEGRAM_API_ID / TELEGRAM_API_HASH
 *   2. local.properties (never committed)
 *   3. gradle.properties
 * Obtain your own pair at https://my.telegram.org/apps — the Telegram ToS
 * forbid shipping an application with someone else's api_id.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String, fallback: String): String =
    System.getenv(name)
        ?: localProperties.getProperty(name)
        ?: (project.findProperty(name) as String?)
        ?: fallback

val telegramApiId = secret("TELEGRAM_API_ID", "0").trim().ifEmpty { "0" }
val telegramApiHash = secret("TELEGRAM_API_HASH", "").trim()

android {
    namespace = "com.tvgram"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tvgram"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")

        ndk {
            // TDLib prebuilts are usually shipped for these four ABIs.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.media3.common.util.UnstableApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    sourceSets["main"].jniLibs.srcDirs("libs/jniLibs")
    // TDLib's Android build emits plain .java sources; dropping them in libs/java is the
    // shortest path from "I built TDLib" to "the app compiles" — no jar packaging step.
    sourceSets["main"].java.srcDirs("libs/java")

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
        jniLibs.useLegacyPackaging = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // --- TDLib -------------------------------------------------------------
    // Drop the TDLib Android artifacts into app/libs (see app/libs/README.md and
    // docs/BUILDING_TDLIB.md): either an .aar, or tdlib.jar + libs/jniLibs/<abi>/libtdjni.so.
    implementation(fileTree("libs") { include("*.jar", "*.aar") })

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.tv.material)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

/** Fail early with an actionable message instead of a wall of "unresolved reference: TdApi". */
val verifyTdlib by tasks.registering {
    group = "verification"
    description = "Checks that the TDLib artifacts are present in app/libs."
    doLast {
        val libsDir = project.file("libs")
        val binaries = libsDir.listFiles { f -> f.extension == "jar" || f.extension == "aar" }.orEmpty()
        val sources = project.file("libs/java/org/drinkless/tdlib/TdApi.java").exists()
        if (binaries.isEmpty() && !sources) {
            throw GradleException(
                """
                TDLib was not found.

                Put one of the following in place and build again:
                  app/libs/tdlib.aar
                  app/libs/tdlib.jar          + app/libs/jniLibs/<abi>/libtdjni.so
                  app/libs/java/org/drinkless/tdlib/*.java + app/libs/jniLibs/<abi>/libtdjni.so

                See android-tv-telegram/docs/BUILDING_TDLIB.md for the step-by-step build.
                """.trimIndent(),
            )
        }
        val jniDir = project.file("libs/jniLibs")
        val hasNative = jniDir.walkTopDown().any { it.name == "libtdjni.so" }
        if (!hasNative && binaries.none { it.extension == "aar" }) {
            logger.warn(
                "WARNING: no libtdjni.so under app/libs/jniLibs — the app will compile " +
                    "but crash on startup with UnsatisfiedLinkError.",
            )
        }
        if (telegramApiId == "0" || telegramApiHash.isEmpty()) {
            logger.warn(
                "WARNING: TELEGRAM_API_ID / TELEGRAM_API_HASH are not set — " +
                    "the app will build but authorization will fail at runtime.",
            )
        }
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(verifyTdlib)
}
