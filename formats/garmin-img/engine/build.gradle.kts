/*
 * Garmin IMG engine, imported from kmp-garmin-img (M4.1, D33): the IMG reader (TRE, RGN, LBL,
 * NET, TYP), the style resolver and the renderer over its own `RenderTarget`, in
 * `com.asamm.vectormaps.garminimg`.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test-junit5"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")

tasks.test {
    useJUnitPlatform()
    inputs.dir(fixturesDir)
    systemProperty("fixturesDir", fixturesDir.asFile.absolutePath)
}
