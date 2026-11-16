/*
 * MVT AWT graphics: the MVT engine's `MvtRenderTarget` over `java.awt.Graphics2D` and
 * `AwtMvtGraphics` for the driver, for the desktop viewer, the driver and golden tests
 * (`dev/mvt/golden`) and benchmarks.
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
