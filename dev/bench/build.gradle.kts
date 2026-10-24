/*
 * JMH benchmarks of the renderers on the JVM. `./gradlew :dev:bench:jmh`; results in
 * build/results/jmh/results.json. Another map: `-Pbench.mapFile=<path>`, another centre than the
 * map's start position: `-Pbench.center=<lat>,<lon>`. Not published.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jmh)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    jmh(project(":formats:mapsforge:legacy:map-awt"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures").asFile
val mapFile = providers.gradleProperty("bench.mapFile")
    .orElse(File(fixturesDir, "maps/monaco.osm.map").absolutePath)
val center = providers.gradleProperty("bench.center").orElse("")

jmh {
    jmhVersion = libs.versions.jmh
    fork = 1
    warmupIterations = 3
    warmup = "2s"
    iterations = 5
    timeOnIteration = "2s"
    profilers = listOf("gc")
    resultFormat = "JSON"
    jvmArgs = listOf(
        "-Xms2g",
        "-Xmx2g",
        "-Dbench.fixturesDir=${fixturesDir.absolutePath}",
        "-Dbench.mapFile=${mapFile.get()}",
        "-Dbench.center=${center.get()}",
    )
}

// a benchmark run is a measurement, never up to date
tasks.named("jmh") {
    outputs.upToDateWhen { false }
}

// CI runs `build`; compiling the benchmarks there catches breakage from changes they call into
tasks.named("assemble") {
    dependsOn("jmhClasses")
}
