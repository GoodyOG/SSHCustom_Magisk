// Top-level Gradle script.
//
// Reads the root VERSION file once and exposes `sshcVersion` + `sshcVersionCode`
// on the project so the app module can stay in lockstep with the Go daemon
// and the Magisk module.prop. Bump `VERSION` and the next CI build picks it up
// everywhere.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val versionFile = rootProject.file("VERSION")
val sshcVersion: String = if (versionFile.exists()) versionFile.readText().trim() else "0.0.0"

// Magisk-style versionCode: major * 10000 + minor * 100 + patch.
// 2.0.0 -> 20000, 2.1.3 -> 20103, 10.20.7 -> 102007.
val sshcVersionCode: Int = run {
    val parts = sshcVersion.split('.').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
    val major = parts.getOrNull(0) ?: 0
    val minor = parts.getOrNull(1) ?: 0
    val patch = parts.getOrNull(2) ?: 0
    major * 10000 + minor * 100 + patch
}

extra["sshcVersion"] = sshcVersion
extra["sshcVersionCode"] = sshcVersionCode
