/*
 * The MapLibre parity sweep: renders sampled tiles of a large PMTiles file through the MVT driver,
 * compares them with MapLibre renders of `dev/mvt/reference/sweep*.mjs` (Native, or GL JS) and
 * writes a ranked report. `./gradlew :dev:mvt:sweep:sweep -Psweep.args="compare map=<file.pmtiles>
 * run=<dir> ..."`, see Main.kt; `sweepInstall -Psweep.into=<dir>` replaces that folder's content
 * with the tool and its classpath, to score a frozen build while the sources change. Not
 * published, not run by the build.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":formats:mvt:awt"))
    implementation(project(":formats:mvt:driver"))
    implementation(libs.kotlinxSerializationJson)
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures")

tasks.register<JavaExec>("sweep") {
    description = "Runs the MVT parity sweep, arguments in -Psweep.args"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.asamm.vectormaps.mvt.sweep.MainKt")
    args = providers.gradleProperty("sweep.args").map { it.trim().split(Regex("\\s+")) }.getOrElse(emptyList())
    systemProperty("fixturesDir", fixturesDir.asFile.absolutePath)
    workingDir = rootDir
    maxHeapSize = "8g"
}

tasks.register<Sync>("sweepInstall") {
    description = "Replaces the content of -Psweep.into, a folder of its own, with the sweep and its classpath"
    from(tasks.jar, configurations.runtimeClasspath)
    into(providers.gradleProperty("sweep.into").orElse(layout.buildDirectory.dir("sweep").map { it.asFile.path }))
}
