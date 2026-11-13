/*
 * MVT Android graphics: the MVT engine's `MvtRenderTarget` over `android.graphics.Canvas` and
 * `AndroidMvtGraphics` for the driver, used by Locus Map and the Android viewer.
 */
plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.asamm.vectormaps.mvt.android"
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
    api(project(":formats:mvt:engine"))
}
