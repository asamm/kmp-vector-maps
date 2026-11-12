/*
 * MVT AWT target: the MVT engine's `MvtRenderTarget` over `java.awt.Graphics2D`, for the desktop
 * viewer, goldens and benchmarks.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":formats:mvt:engine"))
    testImplementation(kotlin("test-junit5"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")

tasks.test {
    useJUnitPlatform()
    inputs.dir(fixturesDir)
    systemProperty("fixturesDir", fixturesDir.asFile.absolutePath)
    systemProperty("outDir", layout.buildDirectory.dir("renders").get().asFile.absolutePath)
}
