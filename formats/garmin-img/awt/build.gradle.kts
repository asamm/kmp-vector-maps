/*
 * Garmin IMG AWT render target: `Graphics2D` for desktop tools and the golden test.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":formats:garmin-img:engine"))
}
