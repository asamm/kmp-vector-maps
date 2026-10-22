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

dependencies {
    api(project(":formats:mapsforge:legacy:map-reader"))
    api(libs.androidSvg)
}
