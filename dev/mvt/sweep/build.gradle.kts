/*
 * The MapLibre parity sweep: renders sampled tiles of a large PMTiles file through the MVT driver,
 * compares them with MapLibre renders of `dev/mvt/reference/sweep*.mjs` (Native, or GL JS) and
 * writes a ranked report. `./gradlew :dev:mvt:sweep:sweep -Psweep.args="compare map=<file.pmtiles>
 * run=<dir> ..."`, see Main.kt; `sweepInstall -Psweep.into=<dir>` replaces that folder's jars
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

// a plain copy, as a Sync would fingerprint and empty whatever folder it is given
tasks.register("sweepInstall") {
    description = "Replaces the jars in -Psweep.into (from the repository root), a folder of jars only, with the sweep and its classpath"
    val jars = files(tasks.jar, configurations.runtimeClasspath)
    dependsOn(jars)
    val root = rootDir
    val target = providers.gradleProperty("sweep.into")
        .map { root.resolve(it) }
        .orElse(layout.buildDirectory.dir("sweep").map { it.asFile })
    doLast {
        val dir = target.get()
        val other = dir.listFiles()?.filter { !it.isFile || it.extension != "jar" }.orEmpty()
        if (other.isNotEmpty()) {
            throw GradleException("$dir holds more than jars (${other.first().name}); install into a folder of its own")
        }
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        jars.forEach { it.copyTo(File(dir, it.name)) }
    }
}
