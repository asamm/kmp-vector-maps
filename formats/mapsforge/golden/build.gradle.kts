/*
 * Golden render test: renders the corpus with the vendored modules and compares it pixel by pixel
 * with the upstream 0.30.0 render from `:formats:mapsforge:golden-upstream`. Not published.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
    sourceSets.test {
        kotlin.srcDir("harness/kotlin")
    }
}

dependencies {
    testImplementation(project(":formats:mapsforge:legacy:map-awt"))
    testImplementation(kotlin("test-junit5"))
}

val goldenDir = layout.buildDirectory.dir("golden")
val upstreamRender = project(":formats:mapsforge:golden-upstream").layout.buildDirectory.dir("golden/expected")

tasks.test {
    useJUnitPlatform()
    dependsOn(":formats:mapsforge:golden-upstream:renderGoldens")
    inputs.dir(upstreamRender)
    inputs.dir(rootProject.layout.projectDirectory.dir("fixtures"))
    // keeps the rendered PNGs and diffs when the result comes from the build cache
    outputs.dir(goldenDir)
    systemProperty("golden.fixturesDir", rootProject.layout.projectDirectory.dir("fixtures").asFile.absolutePath)
    systemProperty("golden.expectedDir", upstreamRender.get().asFile.absolutePath)
    systemProperty("golden.actualDir", goldenDir.get().dir("actual").asFile.absolutePath)
    systemProperty("golden.diffDir", goldenDir.get().dir("diff").asFile.absolutePath)
}
