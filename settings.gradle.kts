pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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

        // upstream Mapsforge for the golden corpus only
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.mapsforge.mapsforge")
            }
        }
    }
}

rootProject.name = "kmp-vector-maps"

// Mapsforge format: vendored upstream (legacy/, removed after the Kotlin migration) and golden corpus
include(":formats:mapsforge:legacy:core")
include(":formats:mapsforge:legacy:map")
include(":formats:mapsforge:legacy:map-reader")
include(":formats:mapsforge:legacy:map-android")
include(":formats:mapsforge:legacy:map-awt")
include(":formats:mapsforge:golden")
include(":formats:mapsforge:golden-upstream")

// development tools
include(":viewer:common")
include(":viewer:desktop")
include(":viewer:android")
