/*
 * Garmin IMG golden test: Monaco tiles through the Garmin driver against committed PNGs, with the
 * bundled DejaVu Sans so they match on every system, and the dump comparison against the legacy
 * Java parser (libGarminImg, kept as test sources). The benchmark and the comparison sheet run on
 * request: `-Pbench.garmin=true`, options in RenderBenchmarkTest. Not published.
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(project(":formats:garmin-img:awt"))
    testImplementation(project(":formats:garmin-img:driver"))
    testImplementation(kotlin("test-junit5"))
}

// the dump and render tests read the engine's internals, as they did inside kmp-garmin-img
val engine = project(":formats:garmin-img:engine")
tasks.named<KotlinCompile>("compileTestKotlin") {
    friendPaths.from(
        engine.layout.buildDirectory.dir("classes/kotlin/main"),
        engine.tasks.named<Jar>("jar").flatMap { it.archiveFile },
    )
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")
val benchProperties = providers.gradlePropertiesPrefixedBy("bench.garmin")

tasks.test {
    useJUnitPlatform()
    inputs.dir(fixturesDir)
    systemProperty("fixturesDir", fixturesDir.asFile.absolutePath)
    // only the golden candidates are the output: the tens of MB of dumps and review renders under
    // test-output/ stay out of the build cache, and a failing run, which CI uploads, is never cached
    val candidates = layout.buildDirectory.dir("test-output/render-candidates")
    outputs.dir(candidates)
    doFirst {
        candidates.get().asFile.deleteRecursively()
    }
    val bench = benchProperties.get()
    bench.forEach { (key, value) -> systemProperty(key, value) }
    // a requested benchmark measures again, even with nothing changed
    if (bench["bench.garmin"] == "true") {
        outputs.upToDateWhen { false }
    }
}
