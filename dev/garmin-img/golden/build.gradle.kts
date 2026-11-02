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
    // golden candidates, dumps and renders, which CI uploads when a test fails; tens of MB of dumps,
    // so never stored in the build cache
    outputs.dir(layout.buildDirectory.dir("test-output"))
    outputs.cacheIf { false }
    val bench = benchProperties.get()
    bench.forEach { (key, value) -> systemProperty(key, value) }
    // a requested benchmark measures again, even with nothing changed
    if (bench.isNotEmpty()) {
        outputs.upToDateWhen { false }
    }
}
