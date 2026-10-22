/*
 * Android viewer — development tool for on-device render checks. Not published.
 *
 * `./gradlew :viewer:android:installDebug`. The fixtures (Monaco map, LoMaps theme) ship as assets;
 * another map can be passed as an intent extra, see MainActivity.
 */
plugins {
    // AGP 9 has built-in Kotlin support; no kotlin-android plugin
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.asamm.mapsforge.viewer.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.asamm.mapsforge.viewer.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main") {
        assets.directories.add(rootProject.layout.projectDirectory.dir("fixtures").asFile.absolutePath)
    }

    androidResources {
        // .map files must stay uncompressed to be copied out quickly
        noCompress += "map"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":formats:mapsforge:legacy:map-android"))
    implementation(project(":viewer:common"))
    implementation(libs.kotlinxCoroutinesAndroid)
}
