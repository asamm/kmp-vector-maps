/*
 * Format-neutral core: driver interface, registry, styles, render configuration. Every format
 * module depends on it; it depends on nothing but the Kotlin standard library.
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

tasks.test {
    useJUnitPlatform()
}
