// SSHCustom companion app — WebView shell + root integration
//
// Architecture: The entire dashboard UI is served by the daemon at
// http://127.0.0.1:9190/. This app is a thin wrapper providing:
//   - Foreground service (persistent notification via SSE)
//   - Quick Settings Tile (one-tap start/stop)
//   - Boot receiver (autostart)
//   - Root access for daemon lifecycle via libsu

import java.io.File
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

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
    }

    signingConfigs {
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = true }

    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity)

    // libsu for root shell access
    implementation(libs.libsu.core)

    // OkHttp for health/status API + SSE stream (notification updates)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
