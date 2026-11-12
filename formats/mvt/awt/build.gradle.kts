/*
 * MVT AWT target: the MVT engine's `MvtRenderTarget` over `java.awt.Graphics2D`, for the desktop
 * viewer, the golden test (`dev/mvt/golden`) and benchmarks.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":formats:mvt:engine"))
}
