/*
 * Golden render test: renders the corpus with the vendored modules and compares it pixel by pixel
 * with the upstream 0.30.0 render from `:formats:mapsforge:golden-upstream`. Not published.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
    sourceSets.main {
        kotlin.srcDir("harness/common/kotlin")
        kotlin.srcDir("harness/awt/kotlin")
    }
}

dependencies {
    implementation(project(":formats:mapsforge:legacy:map-awt"))
    testImplementation(kotlin("test-junit5"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")
val actualDir = layout.buildDirectory.dir("golden/actual").get().asFile
val diffDir = layout.buildDirectory.dir("golden/diff").get().asFile
val upstreamRender = project(":formats:mapsforge:golden-upstream").layout.buildDirectory.dir("golden/expected")

// Same entry point and JVM flags as `:formats:mapsforge:golden-upstream:renderGoldens`, so both
// renders get the same identity hashes and match exactly (see that task).
val renderVendored = tasks.register<JavaExec>("renderVendored") {
    description = "Renders the golden corpus with the vendored Mapsforge modules."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.asamm.mapsforge.golden.MainKt"
    jvmArgs(
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:hashCode=3",
        "-XX:+UseSerialGC",
        "-Xms2g",
        "-Xmx2g",
    )
    inputs.dir(fixturesDir)
    outputs.dir(actualDir)
    // a task-local copy: the configuration cache cannot capture script values
    val outDir = actualDir
    doFirst {
        outDir.deleteRecursively()
    }
    args(fixturesDir.asFile.absolutePath, actualDir.absolutePath)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(renderVendored, ":formats:mapsforge:golden-upstream:renderGoldens")
    inputs.dir(upstreamRender)
    inputs.dir(actualDir)
    inputs.dir(fixturesDir)
    // keeps the diff images when the result comes from the build cache
    outputs.dir(diffDir)
    // a task-local copy: the configuration cache cannot capture script values
    val staleDiffs = diffDir
    doFirst {
        staleDiffs.deleteRecursively()
    }
    systemProperty("golden.fixturesDir", fixturesDir.asFile.absolutePath)
    systemProperty("golden.expectedDir", upstreamRender.get().asFile.absolutePath)
    systemProperty("golden.actualDir", actualDir.absolutePath)
    systemProperty("golden.diffDir", diffDir.absolutePath)
}
