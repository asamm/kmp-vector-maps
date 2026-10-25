/*
 * Compose Desktop viewer — development tool for visual checks and render performance, for every
 * registered format. Not published.
 *
 * `./gradlew :viewer:desktop:run`. The first start opens the map and style from `local.properties`
 * (`viewer.mapFile`, `viewer.themeFile`), else the committed fixtures; later starts restore the last
 * session. Saved views go to `captures/` in the repo root.
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
    implementation(project(":viewer:common"))
    implementation(project(":formats:mapsforge:driver"))
    implementation(project(":formats:mapsforge:legacy:map-awt"))
    implementation(compose.desktop.currentOs)
    implementation(libs.composeMaterial3)
    implementation(libs.kotlinxCoroutinesSwing)
    testImplementation(kotlin("test-junit5"))
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
        mainClass = "com.asamm.vectormaps.viewer.MainKt"
        jvmArgs += listOf(
            "-Dviewer.mapFile=" + (localProps.getProperty("viewer.mapFile")
                ?: File(fixtures, "maps/monaco.osm.map").absolutePath),
            "-Dviewer.themeFile=" + (localProps.getProperty("viewer.themeFile")
                ?: File(fixtures, "themes/lomaps/theme.xml").absolutePath),
            "-Dviewer.capturesDir=" + rootProject.file("captures").absolutePath,
        )
    }
}

tasks.test {
    useJUnitPlatform()
    inputs.dir(fixtures)
    systemProperty("fixturesDir", fixtures.absolutePath)
}
