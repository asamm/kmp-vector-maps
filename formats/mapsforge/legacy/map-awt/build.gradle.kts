plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    api(project(":formats:mapsforge:legacy:map-reader"))
    api(libs.kxml2)
    api(libs.svgSalamander)
}
