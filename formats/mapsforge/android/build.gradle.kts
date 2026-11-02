/*
 * Mapsforge Android graphics and `AndroidParameters`, vendored and converted to Kotlin (M5.3b, D24),
 * through AGP 9 built-in Kotlin.
 */
plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.asamm.vectormaps.mapsforge.map.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":formats:mapsforge:engine"))
    api(libs.androidSvg)
}
