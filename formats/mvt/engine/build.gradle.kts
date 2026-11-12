/*
 * MVT engine: Mapbox Vector Tile decoding, the MapLibre style subset the Asamm styles use, the
 * renderer over its own `MvtRenderTarget` and the symbol layout, in `com.asamm.vectormaps.mvt`
 * (M10, docs/mvt.md).
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinxSerializationJson)
    testImplementation(kotlin("test-junit5"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")

tasks.test {
    useJUnitPlatform()
    inputs.dir(fixturesDir)
    systemProperty("fixturesDir", fixturesDir.asFile.absolutePath)
}
