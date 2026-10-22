/*
 * Viewer logic shared by the desktop and Android viewers (viewport maths). Not published.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":formats:mapsforge:legacy:core"))
}
