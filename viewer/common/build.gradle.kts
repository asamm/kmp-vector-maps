/*
 * Viewer logic shared by the desktop and Android viewers: viewport maths and the tile engine over
 * any `core` renderer. Not published.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core"))
    implementation(libs.kotlinxCoroutinesCore)

    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
