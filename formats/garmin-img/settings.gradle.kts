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
    repositories {
        google()
        mavenCentral()

        // kmp-logger from GitHub Packages — auth'd via either
        // GPR_USERNAME + GPR_TOKEN (kmp-* projects' own convention) or
        // REPO_GITHUB in `username|token` form (locus-core convention).
        maven {
            name = "kmpLoggerGitHubPackages"
            url = uri("https://maven.pkg.github.com/asamm/kmp-logger")
            credentials {
                val repoGithub = System.getenv("REPO_GITHUB")?.split("|")
                username = System.getenv("GPR_USERNAME")
                    ?: repoGithub?.getOrNull(0)
                password = System.getenv("GPR_TOKEN")
                    ?: repoGithub?.getOrNull(1)
            }
        }
    }
}

rootProject.name = "kmp-garmin-img"

include(":lib")
include(":viewer:desktop")
include(":viewer:android")
