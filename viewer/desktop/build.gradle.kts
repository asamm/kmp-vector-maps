/*
 * Compose Desktop viewer — development tool for visual checks and render performance. Not published.
 *
 * `./gradlew :viewer:desktop:run`. Default map and theme come from `local.properties`
 * (`viewer.mapFile`, `viewer.themeFile`), falling back to the committed fixtures.
 */
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":formats:mapsforge:legacy:map-awt"))
    implementation(project(":viewer:common"))
    implementation(compose.desktop.currentOs)
    implementation(libs.composeMaterial3)
    implementation(libs.kotlinxCoroutinesSwing)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val fixtures = rootProject.layout.projectDirectory.dir("dev/fixtures").asFile

compose.desktop {
    application {
        mainClass = "com.asamm.mapsforge.viewer.MainKt"
        jvmArgs += listOf(
            "-Dviewer.mapFile=" + (localProps.getProperty("viewer.mapFile")
                ?: File(fixtures, "maps/monaco.osm.map").absolutePath),
            "-Dviewer.themeFile=" + (localProps.getProperty("viewer.themeFile")
                ?: File(fixtures, "themes/lomaps/theme.xml").absolutePath),
        )
    }
}
