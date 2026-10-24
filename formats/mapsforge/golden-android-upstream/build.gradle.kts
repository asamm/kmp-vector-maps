/*
 * Renders the golden corpus with unmodified upstream `mapsforge-map-android` 0.30.0 under
 * Robolectric's native Android graphics; `:formats:mapsforge:golden-android` compares against it.
 * Kept in its own module so upstream and vendored `org.mapsforge` classes never share a classpath.
 * Not published.
 */
plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.asamm.mapsforge.golden.android.upstream"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// the shared harness; the variant API, as AGP 9.4 fails on `sourceSets` in library scripts
val harness = rootProject.layout.projectDirectory.dir("formats/mapsforge/golden/harness")
androidComponents {
    // one variant is enough; a release one would render the corpus again into the same directory
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enable = false
    }
    onVariants { variant ->
        variant.hostTests.values.forEach { hostTest ->
            hostTest.sources.kotlin?.addStaticSourceDirectory(harness.dir("common/kotlin").asFile.absolutePath)
            hostTest.sources.kotlin?.addStaticSourceDirectory(harness.dir("android/kotlin").asFile.absolutePath)
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.upstreamMapsforgeAndroid)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures").asFile
val expectedDir = layout.buildDirectory.dir("golden/expected").get().asFile

tasks.withType<Test>().configureEach {
    // same flags as :formats:mapsforge:golden-android; why: IdentityHashes in the harness
    jvmArgs(
        // synchronous JIT: background compilation draws identity hashes at varying moments
        "-Xbatch",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:hashCode=3",
        "-XX:+UseSerialGC",
        "-Xms2g",
        "-Xmx2g",
    )
    inputs.dir(fixturesDir)
    outputs.dir(expectedDir)
    systemProperty("golden.fixturesDir", fixturesDir.absolutePath)
    systemProperty("golden.outDir", expectedDir.absolutePath)
}
