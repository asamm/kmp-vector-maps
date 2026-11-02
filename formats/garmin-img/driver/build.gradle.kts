/*
 * Garmin IMG driver: the `:core` driver interface over the Garmin engine. Takes the graphics
 * backend (AWT, Android) from the host as a `GarminCanvasFactory`.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core"))
    api(project(":formats:garmin-img:engine"))
    testImplementation(project(":formats:garmin-img:awt"))
    testImplementation(kotlin("test-junit5"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")

tasks.test {
    useJUnitPlatform()
    inputs.dir(fixturesDir)
    systemProperty("fixturesDir", fixturesDir.asFile.absolutePath)
}
