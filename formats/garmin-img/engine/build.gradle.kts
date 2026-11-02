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

sourceSets.test {
    // tests read the maps from the classpath, as `maps/<fixture>/<file>`
    resources.srcDir(rootProject.layout.projectDirectory.dir("dev/fixtures"))
}

dependencies {
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
