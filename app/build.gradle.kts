// SSHCustom companion app — root-aware Compose UI for /api/v1/*
//
// Decisions captured in .kiro/steering/v2-roadmap.md:
//   - Hybrid: native Compose for Home/Profiles/Settings, WebView for Logs
//   - Min SDK 26, target SDK 35
//   - Material You (API 31+) with fixed-palette fallback below
//   - libsu for root command execution
//   - Package id permanent: com.sshcustom.app
//
// Release signing: the keystore is NOT committed. The user generates one
// once with `scripts/generate-release-keystore.sh` and uploads four
// repository secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS,
// KEY_PASSWORD) to GitHub. CI decodes the keystore on the fly. If the
// secrets are absent (e.g. fork builds), the release build falls back to
// the debug signing config so unsigned-but-installable APKs are still
// produced — useful for contributors testing changes locally.

import java.io.File
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Read signing material from environment variables (CI) or local properties
// (developer machine). The env-var path is what GitHub Actions populates;
// the keystore.properties path lets a contributor sign locally without
// editing build.gradle.kts. Both are optional — if neither is configured
// the release build uses the Android debug keystore.
val keystoreFile: File? = run {
    val envBase64 = System.getenv("KEYSTORE_BASE64")
    if (!envBase64.isNullOrBlank()) {
        val decoded = layout.buildDirectory.file("ci-release.jks").get().asFile
        decoded.parentFile.mkdirs()
        decoded.writeBytes(Base64.getDecoder().decode(envBase64))
        return@run decoded
    }
    val local = rootProject.file("keystore.properties")
    if (local.exists()) {
        val props = Properties().apply { load(local.inputStream()) }
        val path = props.getProperty("storeFile")?.let { rootProject.file(it) }
        if (path?.exists() == true) return@run path
    }
    null
}

val keystoreStorePassword: String? =
    System.getenv("KEYSTORE_PASSWORD")
        ?: rootProject.file("keystore.properties").takeIf { it.exists() }?.let {
            Properties().apply { load(it.inputStream()) }.getProperty("storePassword")
        }

val keystoreKeyAlias: String? =
    System.getenv("KEY_ALIAS")
        ?: rootProject.file("keystore.properties").takeIf { it.exists() }?.let {
            Properties().apply { load(it.inputStream()) }.getProperty("keyAlias")
        }

val keystoreKeyPassword: String? =
    System.getenv("KEY_PASSWORD")
        ?: rootProject.file("keystore.properties").takeIf { it.exists() }?.let {
            Properties().apply { load(it.inputStream()) }.getProperty("keyPassword")
        }

android {
    namespace = "com.sshcustom.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sshcustom.app"
        minSdk = 26
        targetSdk = 35

        versionCode = rootProject.extra["sshcVersionCode"] as Int
        versionName = rootProject.extra["sshcVersion"] as String

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Only register the "release" config when all four pieces are
        // available; otherwise let release fall back to debug signing.
        if (keystoreFile != null && keystoreStorePassword != null &&
            keystoreKeyAlias != null && keystoreKeyPassword != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreStorePassword
                keyAlias = keystoreKeyAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        // Java 17 — Android Gradle Plugin 8.x requires it; widely available
        // on dev machines and matches what GitHub Actions ships with.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties",
            )
        }
    }
}

dependencies {
    // Compose BOM keeps Material3 + UI artifacts on a single coordinated version
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material.views)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.datastore.preferences)

    // libsu for root shell access. The companion app needs root to call
    // /data/adb/sshcustom/sshcustom.sh (start/stop/restart) and to read
    // /data/adb/sshcustom/profiles.json when the daemon is offline.
    implementation(libs.libsu.core)

    // Networking. OkHttp + kotlinx-serialization is much lighter than
    // Retrofit + Moshi for our 14-endpoint surface, and SSE support is
    // straightforward via okhttp-sse.
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
