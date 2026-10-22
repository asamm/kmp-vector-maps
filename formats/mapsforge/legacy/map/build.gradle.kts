plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    api(project(":formats:mapsforge:legacy:core"))
    // XmlPullParser API only; Android ships its own, AWT adds kxml2 at runtime
    compileOnly(libs.kxml2)
}
