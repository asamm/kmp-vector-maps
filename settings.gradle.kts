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

// Mapsforge format: vendored upstream (legacy/, removed after the Kotlin migration), driver and
// golden corpus
include(":formats:mapsforge:legacy:core")
include(":formats:mapsforge:legacy:map")
include(":formats:mapsforge:legacy:map-reader")
include(":formats:mapsforge:legacy:map-android")
include(":formats:mapsforge:legacy:map-awt")
include(":formats:mapsforge:driver")
include(":formats:mapsforge:golden")
include(":formats:mapsforge:golden-upstream")
include(":formats:mapsforge:golden-android")
include(":formats:mapsforge:golden-android-upstream")

// benchmarks
include(":dev:bench")
include(":dev:bench:android")

// development tools
include(":viewer:common")
include(":viewer:desktop")
include(":viewer:android")
