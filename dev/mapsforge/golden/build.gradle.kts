/*
 * Golden render test: renders the corpus with the vendored modules through the Mapsforge driver and
 * compares it with the plain upstream 0.30.0 render from `:dev:mapsforge:golden-upstream`: dumps
 * exact, tiles without labels within `accepted-differences.txt`; tiles with labels and the label
 * dumps against the committed `labels/awt`, refreshed by `updateLabelGoldens` (D37). Not published.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

// The shared harness imports upstream's packages; this side compiles a copy with the vendored ones.
// Edit `harness/`: the IDE opens the generated copy, which the next build overwrites, and resolves
// it only after a first build.
val vendoredHarness = tasks.register<Sync>("vendoredHarness") {
    // task-local copies: the configuration cache cannot capture script values; a filter is no input
    val upstreamPrefix = "org.mapsforge."
    val vendoredPrefix = "com.asamm.vectormaps.mapsforge."
    inputs.property("upstreamPrefix", upstreamPrefix)
    inputs.property("vendoredPrefix", vendoredPrefix)
    from("harness/common/kotlin")
    from("harness/awt/kotlin")
    into(layout.buildDirectory.dir("generated/harness"))
    filter { line -> line.replace(upstreamPrefix, vendoredPrefix) }
}

kotlin {
    jvmToolchain(17)
    sourceSets.main {
        kotlin.srcDir(vendoredHarness)
    }
}

dependencies {
    implementation(project(":formats:mapsforge:awt"))
    implementation(project(":formats:mapsforge:driver"))
    testImplementation(kotlin("test-junit5"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")
val actualDir = layout.buildDirectory.dir("golden/actual").get().asFile
val diffDir = layout.buildDirectory.dir("golden/diff").get().asFile
val upstreamRender = project(":dev:mapsforge:golden-upstream").layout.buildDirectory.dir("golden/expected")
// confirmed drawing differences against upstream, shared with golden-android (D37)
val acceptedFile = layout.projectDirectory.file("accepted-differences.txt").asFile
// the C-rows an accepted entry may cite
val upstreamDoc = rootProject.layout.projectDirectory.file("docs/upstream.md").asFile
// the committed label goldens, drawn in the bundled font (D37)
val labelsDir = layout.projectDirectory.dir("labels/awt").asFile

// Same entry point and JVM flags as `:dev:mapsforge:golden-upstream:renderGoldens`, so both
// renders get the same identity hashes and match exactly (see that task).
val renderVendored = tasks.register<JavaExec>("renderVendored") {
    description = "Renders the golden corpus with the vendored Mapsforge modules."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.asamm.mapsforge.golden.MainKt"
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
    dependsOn(renderVendored, ":dev:mapsforge:golden-upstream:renderGoldens")
    inputs.dir(upstreamRender)
    inputs.dir(actualDir)
    inputs.dir(fixturesDir)
    inputs.file(acceptedFile)
    inputs.file(upstreamDoc)
    inputs.dir(labelsDir)
    mustRunAfter("updateLabelGoldens")
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
    systemProperty("golden.acceptedFile", acceptedFile.absolutePath)
    systemProperty("golden.upstreamDoc", upstreamDoc.absolutePath)
    systemProperty("golden.labelsDir", labelsDir.absolutePath)
}

tasks.register<Sync>("updateLabelGoldens") {
    description = "Replaces the committed label goldens with the vendored render, for an intended change."
    dependsOn(renderVendored)
    from(actualDir) {
        include("*.png", "*_labels.txt")
        // tiles without labels are compared with upstream
        exclude("*_separate_*.png")
    }
    into(labelsDir)
}
