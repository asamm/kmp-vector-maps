/*
 * JMH benchmarks of the renderers on the JVM. `./gradlew :dev:bench:jmh`; results in
 * build/results/jmh/results.json. Another map: `-Pbench.mapFile=<path>`, another centre than the
 * map's start position: `-Pbench.center=<lat>,<lon>` (or `<lat>:<lon>`, as on the A54); for Garmin
 * `-Pbench.garmin.mapFile` and `-Pbench.garmin.center` (default: the fixture's harbour, with another
 * map its bounds' centre); MVT on the fixture tiles only. One renderer only:
 * `-Pbench.includes=GarminBench`. Not published.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jmh)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    jmh(project(":formats:mapsforge:awt"))
    jmh(project(":formats:garmin-img:driver"))
    jmh(project(":formats:garmin-img:awt"))
    jmh(project(":formats:mvt:driver"))
    jmh(project(":formats:mvt:awt"))
}

val fixturesDir = rootProject.layout.projectDirectory.dir("dev/fixtures").asFile
val mapFile = providers.gradleProperty("bench.mapFile")
    .orElse(File(fixturesDir, "maps/monaco.osm.map").absolutePath)
val center = providers.gradleProperty("bench.center").orElse("")
val garminMapFile = providers.gradleProperty("bench.garmin.mapFile")
    .orElse(File(fixturesDir, "maps/otm-monaco/otm-monaco.img").absolutePath)
// the fixture's harbour; its bounds' centre is a nearly empty tile
val garminCenter = providers.gradleProperty("bench.garmin.center")
    .orElse(providers.gradleProperty("bench.garmin.mapFile").map { "" }.orElse("43.735,7.423"))
val benchIncludes = providers.gradleProperty("bench.includes")

jmh {
    jmhVersion = libs.versions.jmh
    fork = 1
    warmupIterations = 3
    warmup = "2s"
    iterations = 5
    timeOnIteration = "2s"
    profilers = listOf("gc")
    resultFormat = "JSON"
    if (benchIncludes.isPresent) {
        includes.add(benchIncludes.get())
    }
    jvmArgs = listOf(
        "-Xms2g",
        "-Xmx2g",
        "-Dbench.fixturesDir=${fixturesDir.absolutePath}",
        "-Dbench.mapFile=${mapFile.get()}",
        "-Dbench.center=${center.get()}",
        "-Dbench.garmin.mapFile=${garminMapFile.get()}",
        "-Dbench.garmin.center=${garminCenter.get()}",
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
