/*
 * Mapsforge AWT graphics, vendored and converted to Kotlin (M5.3b, D24); the golden test, the
 * benchmarks and the desktop viewer draw through it.
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
}

// as the engine and upstream: Java 8 bytecode against the Java 8 API
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        freeCompilerArgs.add("-Xjdk-release=1.8")
    }
}

// a Java source here would build as the Kotlin does
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}

dependencies {
    api(project(":formats:mapsforge:engine"))
    api(libs.kxml2)
    api(libs.svgSalamander)
}
