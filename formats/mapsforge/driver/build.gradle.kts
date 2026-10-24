/*
 * Mapsforge driver: the `:core` driver interface over the vendored Mapsforge Java. Takes the
 * graphics backend (AWT, Android) from the host as a Mapsforge `GraphicFactory`.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core"))
    api(project(":formats:mapsforge:legacy:map-reader"))
    testImplementation(project(":formats:mapsforge:legacy:map-awt"))
    testImplementation(kotlin("test-junit5"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")

tasks.test {
    useJUnitPlatform()
    inputs.dir(fixturesDir)
    systemProperty("fixturesDir", fixturesDir.asFile.absolutePath)
}
