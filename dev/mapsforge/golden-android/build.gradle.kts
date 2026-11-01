/*
 * Android golden render test: renders the corpus with the vendored `:formats:mapsforge:android`
 * module under Robolectric's native Android graphics and compares it pixel by pixel with the
 * upstream 0.30.0 render from `:dev:mapsforge:golden-android-upstream`. Not published.
 */
plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.asamm.mapsforge.golden.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Copies the shared harness, which imports upstream's packages, with the vendored ones; a task type
 * of its own, as AGP takes generated sources from a `DirectoryProperty`. Edit `harness/`: the IDE
 * opens the generated copy, which the next build overwrites.
 */
abstract class VendoredHarness : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun copy() {
        fileSystem.sync {
            from(sources)
            into(outputDir)
            filter { line -> line.replace("org.mapsforge.", "com.asamm.vectormaps.mapsforge.") }
        }
    }
}

// the shared harness; the variant API, as AGP 9.4 fails on `sourceSets` in library scripts
val harness = rootProject.layout.projectDirectory.dir("dev/mapsforge/golden/harness")
val vendoredHarness = tasks.register<VendoredHarness>("vendoredHarness") {
    sources.from(harness.dir("common/kotlin"), harness.dir("android/kotlin"))
    outputDir = layout.buildDirectory.dir("generated/harness")
}
androidComponents {
    // one variant is enough, as in golden-android-upstream
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enable = false
    }
    onVariants { variant ->
        variant.hostTests.values.forEach { hostTest ->
            hostTest.sources.kotlin?.addGeneratedSourceDirectory(vendoredHarness, VendoredHarness::outputDir)
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(project(":formats:mapsforge:android"))
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures").asFile
val actualDir = layout.buildDirectory.dir("golden/actual").get().asFile
val diffDir = layout.buildDirectory.dir("golden/diff").get().asFile
val upstreamRender = project(":dev:mapsforge:golden-android-upstream").layout.buildDirectory.dir("golden/expected")

tasks.withType<Test>().configureEach {
    dependsOn(":dev:mapsforge:golden-android-upstream:testDebugUnitTest")
    // same flags as golden-android-upstream, so both renders get the same identity hashes
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
    inputs.dir(upstreamRender)
    outputs.dir(actualDir)
    // keeps the diff images when the result comes from the build cache
    outputs.dir(diffDir)
    // a task-local copy: the configuration cache cannot capture script values
    val staleDiffs = diffDir
    doFirst {
        staleDiffs.deleteRecursively()
    }
    systemProperty("golden.fixturesDir", fixturesDir.absolutePath)
    systemProperty("golden.expectedDir", upstreamRender.get().asFile.absolutePath)
    systemProperty("golden.actualDir", actualDir.absolutePath)
    systemProperty("golden.diffDir", diffDir.absolutePath)
}
