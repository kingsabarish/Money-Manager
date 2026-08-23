// Top-level build file. Plugins are declared here (applied in module builds)
// so their versions resolve once from the version catalog (gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    // kotlin-android removed: AGP 9.0+ provides built-in Kotlin support.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
