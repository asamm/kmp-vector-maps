/*
 * Mapsforge map: model, util and datastore in Kotlin since M5.2b (D24), the rest still Java.
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

tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}

dependencies {
    api(project(":formats:mapsforge:legacy:core"))
    // XmlPullParser API only; Android ships its own, AWT adds kxml2 at runtime
    compileOnly(libs.kxml2)
}
