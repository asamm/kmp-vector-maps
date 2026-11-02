/*
 * Garmin IMG golden test: the Monaco render through the AWT target against a committed PNG, and
 * the dump comparison against the legacy Java parser (libGarminImg, kept as test sources). Not
 * published.
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

sourceSets.test {
    // tests read the maps from the classpath, as `maps/<fixture>/<file>`
    resources.srcDir(rootProject.layout.projectDirectory.dir("dev/fixtures"))
}

dependencies {
    testImplementation(project(":formats:garmin-img:awt"))
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

tasks.test {
    useJUnitPlatform()
    // forwards `-Dkmpgi.bench.*` from the Gradle invocation to RenderBenchmarkTest
    System.getProperties()
        .filterKeys { (it as? String)?.startsWith("kmpgi.bench.") == true }
        .forEach { (k, v) -> systemProperty(k as String, v as Any) }
}
