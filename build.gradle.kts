// Root build script — real configuration lives in the modules. Plugins are declared
// `apply false` here so they resolve once and the version catalog stays the single source of
// truth for plugin versions.

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.jmh) apply false
    alias(libs.plugins.androidxBenchmark) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.composeMultiplatform) apply false
}
