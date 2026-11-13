/*
 * MVT Android golden test: renders the fixture tiles through the driver with
 * `:formats:mvt:android` under Robolectric's native Android graphics and compares them with the
 * MapLibre GL JS renders (`dev/mvt/reference`), within the JVM golden's limits; the label pass is
 * drawn over each tile and written out for inspection. Not published.
 */
plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.asamm.vectormaps.mvt.golden.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    // one variant is enough, as in the Mapsforge golden-android
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enable = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(project(":formats:mvt:driver"))
    testImplementation(project(":formats:mvt:android"))
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures").asFile
val rendersDir = layout.buildDirectory.dir("renders").get().asFile

tasks.withType<Test>().configureEach {
    inputs.dir(fixturesDir)
    outputs.dir(rendersDir)
    systemProperty("golden.fixturesDir", fixturesDir.absolutePath)
    systemProperty("golden.rendersDir", rendersDir.absolutePath)
}
