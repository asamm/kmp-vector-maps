/*
 * Renders the golden corpus with unmodified upstream Mapsforge 0.30.0 from JitPack. Kept in its own
 * module so upstream and vendored `org.mapsforge` classes never share a classpath. Not published.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
    sourceSets.main {
        kotlin.srcDir("../golden/harness/kotlin")
    }
}

dependencies {
    implementation(libs.upstreamMapsforgeAwt)
}

val fixturesDir = rootProject.layout.projectDirectory.dir("fixtures")
val expectedDir = layout.buildDirectory.dir("golden/expected")

tasks.register<JavaExec>("renderGoldens") {
    description = "Renders the golden corpus with upstream Mapsforge 0.30.0."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.asamm.mapsforge.golden.MainKt"
    inputs.dir(fixturesDir)
    outputs.dir(expectedDir)
    val outDir = expectedDir.get().asFile
    doFirst {
        outDir.deleteRecursively()
    }
    args(fixturesDir.asFile.absolutePath, outDir.absolutePath)
}
