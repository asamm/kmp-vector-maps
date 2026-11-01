/*
 * Mapsforge map: model, util and datastore (M5.2b), renderer, tile cache, job queue (M5.4b), label
 * stores (M5.5b), theme rules (M5.6a), render instructions (M5.6b) and the XML theme parser (M5.7a) in
 * Kotlin (D24).
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

// a Java source here would build as the other JVM legacy modules do
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}

dependencies {
    api(project(":formats:mapsforge:legacy:core"))
    // XmlPullParser API only; Android ships its own, AWT adds kxml2 at runtime
    compileOnly(libs.kxml2)
}
