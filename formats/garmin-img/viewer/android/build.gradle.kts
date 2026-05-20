/*
 * kmp-garmin-img — Android viewer (development tool).
 *
 * Minimal one-Activity app that loads an IMG from the device's Downloads
 * folder, renders it via AndroidCanvasRenderTarget into a SurfaceView,
 * and supports pan / pinch-zoom touch gestures. Not a published artifact.
 *
 * Install with `./gradlew :viewer:android:installDebug` after putting an
 * IMG file at /sdcard/Download/otm-monaco.img on the test device, or
 * configure `IMG_PATH` in MainActivity.kt before building.
 */
plugins {
    // AGP 9 ships with built-in Kotlin support; the `kotlin-android`
    // plugin is no longer required (and applying it fails the build).
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.asamm.garminimg.viewer.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.asamm.garminimg.viewer.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        viewBinding = false
    }
}

dependencies {
    implementation(project(":lib"))
}
