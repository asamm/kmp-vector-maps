/*
 * Mapsforge Android graphics and `AndroidParameters`, in Kotlin since M5.3b (D24), through AGP 9
 * built-in Kotlin.
 */
plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "org.mapsforge.map.android"
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
    api(project(":formats:mapsforge:legacy:map-reader"))
    api(libs.androidSvg)
}
