pluginManagement {
    repositories {
        google()
        mavenCentral()
        // only the foojay toolchain resolver and the JMH plugin come from the plugin portal
        gradlePluginPortal {
            content {
                includeGroupAndSubgroups("org.gradle.toolchains")
                includeGroup("me.champeau.jmh")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // upstream Mapsforge for the golden corpus only; nothing else may come from JitPack
        exclusiveContent {
            forRepository {
                maven {
                    url = uri("https://jitpack.io")
                }
            }
            filter {
                includeGroup("com.github.mapsforge.mapsforge")
            }
        }
    }
}

rootProject.name = "kmp-vector-maps"

// format-neutral core: driver interface, styles, render configuration
include(":core")

// Mapsforge format: the vendored engine and its Android and AWT graphics (Kotlin since M5), driver
include(":formats:mapsforge:engine")
include(":formats:mapsforge:android")
include(":formats:mapsforge:awt")
include(":formats:mapsforge:driver")

// Mapsforge golden corpus: pixel tests against upstream 0.30.0, not published
include(":dev:mapsforge:golden")
include(":dev:mapsforge:golden-upstream")
include(":dev:mapsforge:golden-android")
include(":dev:mapsforge:golden-android-upstream")

// Garmin IMG format: the engine (kmp-garmin-img, M4.1), its Android and AWT render targets, the driver
include(":formats:garmin-img:engine")
include(":formats:garmin-img:android")
include(":formats:garmin-img:awt")
include(":formats:garmin-img:driver")

// Garmin IMG golden test and dump comparison, not published
include(":dev:garmin-img:golden")

// MVT format (M10 spike): the engine and its AWT render target
include(":formats:mvt:engine")
include(":formats:mvt:awt")

// MVT golden test against MapLibre renders, not published
include(":dev:mvt:golden")

// benchmarks
include(":dev:bench")
include(":dev:bench:android")

// development tools
include(":viewer:common")
include(":viewer:desktop")
include(":viewer:android")
