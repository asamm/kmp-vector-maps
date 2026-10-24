/*
 * Android microbenchmarks of the renderers, run on a device as instrumented tests:
 * `./gradlew :dev:bench:android:connectedReleaseAndroidTest`. Another map on the device:
 * `-Pandroid.testInstrumentationRunnerArguments.bench.mapFile=<device path>`, a centre:
 * `...bench.center=<lat>,<lon>`; a map in shared storage needs
 * `adb shell appops set com.asamm.vectormaps.bench.android.test MANAGE_EXTERNAL_STORAGE allow`.
 * Results in build/outputs/connected_android_test_additional_output. Not published.
 */
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.androidxBenchmark)
}

android {
    namespace = "com.asamm.vectormaps.bench.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    // benchmarks must not run debuggable
    testBuildType = "release"
    buildTypes {
        release {
            isDefault = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "map"
    }
}

// the fixtures as test assets; the variant API, as AGP 9.4 fails on `sourceSets` in library scripts
val fixturesPath = rootProject.layout.projectDirectory.dir("dev/fixtures").asFile.absolutePath
androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory(fixturesPath)
    }
}

kotlin {
    jvmToolchain(17)
}

// CI runs `build`; compiling the benchmarks there catches breakage from changes they call into
tasks.named("assemble") {
    dependsOn("compileReleaseAndroidTestKotlin")
}

dependencies {
    androidTestImplementation(project(":formats:mapsforge:legacy:map-android"))
    androidTestImplementation(libs.androidxBenchmarkJunit4)
    androidTestImplementation(libs.androidxTestExtJunit)
}
