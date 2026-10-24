plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// as upstream: Java 8 bytecode, UTF-8 sources
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}

dependencies {
    api(project(":formats:mapsforge:legacy:core"))
    // XmlPullParser API only; Android ships its own, AWT adds kxml2 at runtime
    compileOnly(libs.kxml2)
}
