pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // kmp-logger is published to GitHub Packages — auth'd via GPR_USERNAME/GPR_TOKEN
        // (REPO_GITHUB is accepted as a username fallback to match locus-core conventions).
        maven {
            name = "kmpLoggerGitHubPackages"
            url = uri("https://maven.pkg.github.com/asamm/kmp-logger")
            credentials {
                username = System.getenv("GPR_USERNAME") ?: System.getenv("REPO_GITHUB") ?: ""
                password = System.getenv("GPR_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "kmp-garmin-img"

include(":lib")
include(":viewer")
