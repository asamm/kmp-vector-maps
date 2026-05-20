import com.asamm.gradle.moduleVersion
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    `maven-publish`
}

val modulePackage = "com.asamm.kmp"
val moduleArtifact = "kmp-garmin-img"

group = modulePackage
version = moduleVersion(libs.versions.version.get())

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Phase A targets: Android (Locus Map) + JVM (local testing). iOS deferred.
    android {
        namespace = "$modulePackage.garminimg"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    // Kotlin multiplatform's `jvm()` target auto-compiles Java sources
    // sitting alongside Kotlin in `jvmMain` / `jvmTest`. We use that to
    // ship the legacy libGarminImg Java parser inside the jvmTest source
    // set so the dump-diff regression test can run our parser and the
    // reference Java parser side-by-side.
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kmpLogger)
            implementation(libs.kotlinxCoroutinesCore)
            implementation(libs.kotlinxIoCore)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Intermediate source set shared by the JVM and Android targets so
        // code that relies on `java.io.*` (e.g. RandomAccessFile-backed
        // IMG reader) lives in one place. AGP 9's KMP variant doesn't
        // create this hierarchy automatically.
        val jvmAndAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmMain.get().dependsOn(jvmAndAndroidMain)
        jvmTest.dependencies {
            // Route the multiplatform kotlin.test annotations to the JUnit 5 (Jupiter)
            // engine on the JVM target. Without this they default to JUnit 4, which we
            // don't want — locus-core uses JUnit 5 across the board.
            implementation(kotlin("test-junit5"))
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    repositories {
        // GitHub Packages — auth'd channel for the team. REPO_GITHUB is accepted as a
        // username fallback so the same env var used by locus-core works here too.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/asamm/kmp-garmin-img")
            credentials {
                username = System.getenv("GPR_USERNAME") ?: System.getenv("REPO_GITHUB")
                password = System.getenv("GPR_TOKEN")
            }
        }
    }

    publications.withType<MavenPublication>().configureEach {
        // The Gradle module is `:lib`, but the public Maven artifact must be
        // `kmp-garmin-img[-<target>]`. Rewrite the leading `lib` segment so each
        // platform variant publishes under the right coordinate.
        artifactId = artifactId.replaceFirst(Regex("^lib"), moduleArtifact)
        pom {
            name.set(moduleArtifact)
            description.set("KMP parser & renderer for unencrypted Garmin IMG vector maps.")
            url.set("https://github.com/asamm/kmp-garmin-img")
            licenses {
                license {
                    name.set("GNU Lesser General Public License, version 2.1")
                    url.set("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt")
                }
            }
            scm {
                url.set("https://github.com/asamm/kmp-garmin-img")
                connection.set("scm:git:https://github.com/asamm/kmp-garmin-img.git")
                developerConnection.set("scm:git:ssh://git@github.com/asamm/kmp-garmin-img.git")
            }
            developers {
                developer {
                    organization.set("Asamm Software")
                    organizationUrl.set("https://www.asamm.com")
                }
            }
        }
    }
}
