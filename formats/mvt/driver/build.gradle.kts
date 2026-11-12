/*
 * MVT driver: the `:core` interfaces over the MVT engine; for now its label pass (M10.1), the
 * driver itself follows in M10.2.
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
}
