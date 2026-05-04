/*
 * kmp-garmin-img — Compose Multiplatform Desktop viewer (development tool).
 *
 * Not a published artifact. Run with `./gradlew :viewer:run` or build a
 * native bundle with `./gradlew :viewer:packageDistributionForCurrentOS`.
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.composeMultiplatform)
}

group = "com.asamm.kmp.garminimg.viewer"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":lib"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "com.asamm.garminimg.viewer.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "kmp-garmin-img-viewer"
            // Compose Desktop's nativeDistributions requires MAJOR >= 1.
            // The library version stays at 0.1.0; this is just the bundle tag.
            packageVersion = "1.0.0"
            description = "Desktop viewer for Garmin IMG vector maps."
        }
    }
}
