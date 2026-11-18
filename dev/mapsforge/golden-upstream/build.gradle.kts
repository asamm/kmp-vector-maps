/*
 * Renders the golden corpus with unmodified upstream Mapsforge 0.30.0 from JitPack. Kept in its own
 * module so upstream and vendored classes never share a classpath. Not published.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
    sourceSets.main {
        kotlin.srcDir("../golden/harness/common/kotlin")
        kotlin.srcDir("../golden/harness/awt/kotlin")
    }
}

dependencies {
    implementation(libs.upstreamMapsforgeAwt)
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")
val expectedDir = layout.buildDirectory.dir("golden/expected")

tasks.register<JavaExec>("renderGoldens") {
    description = "Renders the golden corpus with upstream Mapsforge 0.30.0."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.asamm.mapsforge.golden.MainKt"
    // same flags as :dev:mapsforge:golden:renderVendored; why: IdentityHashes in the harness
    jvmArgs(
        // synchronous JIT: background compilation draws identity hashes at varying moments
        "-Xbatch",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:hashCode=3",
        "-XX:+UseSerialGC",
        "-Xms2g",
        "-Xmx2g",
    )
    inputs.dir(fixturesDir)
    outputs.dir(expectedDir)
    val outDir = expectedDir.get().asFile
    doFirst {
        outDir.deleteRecursively()
    }
    args(fixturesDir.asFile.absolutePath, outDir.absolutePath)
}

// a sweep over many tiles of a large map against the other side, `-Psweep.args="<map> <theme>
// <menuLayer> <tiles.txt> <outDir> <deviceScale> <labelsInTile>"`, paths without spaces; renders
// into `<outDir>/upstream`, see the harness's Sweep
tasks.register<JavaExec>("sweep") {
    description = "Renders a tile list of a large map for the Mapsforge sweep."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.asamm.mapsforge.golden.MainKt"
    // same flags as renderGoldens
    jvmArgs(
        "-Xbatch",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:hashCode=3",
        "-XX:+UseSerialGC",
        "-Xms2g",
        "-Xmx2g",
    )
    val sweepArgs = providers.gradleProperty("sweep.args")
        .map { it.trim().split(Regex("\\s+")) }
        .getOrElse(emptyList())
    args = listOf("sweep") + sweepArgs + "upstream"
    workingDir = rootDir
}
