/*
 * MVT driver: the `:core` driver interface over the MVT engine, its PMTiles and host tile sources
 * and its label pass. Takes the graphics backend (AWT, Android) from the host as `MvtGraphics`.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core"))
    api(project(":formats:mvt:engine"))
    testImplementation(project(":formats:mvt:awt"))
    testImplementation(kotlin("test-junit5"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")

tasks.test {
    useJUnitPlatform()
    inputs.dir(fixturesDir)
    systemProperty("fixturesDir", fixturesDir.asFile.absolutePath)
}
