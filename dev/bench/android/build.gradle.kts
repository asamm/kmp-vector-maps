/*
 * Android microbenchmarks of the renderers, run on a device as instrumented tests:
 * `./gradlew :dev:bench:android:connectedReleaseAndroidTest`. Another map on the device:
 * `-Pandroid.testInstrumentationRunnerArguments.bench.mapFile=<device path>`, a centre:
 * `...bench.center=<lat>,<lon>`; for Garmin `...bench.garmin.mapFile` and `...bench.garmin.center`
 * (`GarminBenchmarks`); one class: `...class=<fully qualified name>`; a map in shared storage needs
 * `adb shell appops set com.asamm.vectormaps.bench.android.test MANAGE_EXTERNAL_STORAGE allow`.
 * Results in build/outputs/connected_android_test_additional_output. The M5 soak, skipped
 * without `soak.minutes`: see `SoakTest`; native and Java heap per Paint, skipped without
 * `bench.paintCost`: see `PaintCostTest`. Not published.
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
        noCompress += listOf("map", "img")
        // the golden font folder stays out. A set list replaces aapt's default ignores, so they are
        // repeated first
        ignoreAssetsPatterns += listOf("!.svn", "!.git", "!.ds_store", "!*.scc", ".*", "<dir>_*", "!CVS", "!thumbs.db", "!picasa.ini", "!*~", "<dir>fonts")
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
    androidTestImplementation(project(":formats:mapsforge:android"))
    androidTestImplementation(project(":formats:mapsforge:driver"))
    androidTestImplementation(project(":formats:garmin-img:android"))
    androidTestImplementation(project(":formats:garmin-img:driver"))
    androidTestImplementation(project(":viewer:common"))
    androidTestImplementation(libs.androidxBenchmarkJunit4)
    androidTestImplementation(libs.androidxTestExtJunit)
}
