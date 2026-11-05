/*
 * Format-neutral core: driver interface, registry, styles, render configuration, label model. Every
 * format module depends on it; it depends on the Kotlin standard library and coroutines only.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // the label pass prepares off the caller's thread
    implementation(libs.kotlinxCoroutinesCore)
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
