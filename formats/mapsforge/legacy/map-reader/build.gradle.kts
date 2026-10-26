/*
 * Mapsforge `.map` reader, converted from the vendored Java in place (M5.1, D24).
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
}

// as the other JVM legacy modules and upstream: Java 8 bytecode against the Java 8 API
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

dependencies {
    api(project(":formats:mapsforge:legacy:map"))
}
