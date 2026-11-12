/*
 * MVT golden test: the fixture tiles through the MVT engine and its AWT target against MapLibre
 * GL JS renders from `dev/mvt/reference`. Not published.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(project(":formats:mvt:awt"))
    testImplementation(kotlin("test-junit5"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")

tasks.test {
    useJUnitPlatform()
    inputs.dir(fixturesDir)
    systemProperty("fixturesDir", fixturesDir.asFile.absolutePath)
    // the side-by-side strips CI uploads on failure; a cached pass restores them
    val renders = layout.buildDirectory.dir("renders")
    outputs.dir(renders)
    systemProperty("outDir", renders.get().asFile.absolutePath)
    doFirst {
        renders.get().asFile.deleteRecursively()
    }
}
