// Root build script — all real configuration lives in :lib (and later :cli).
// The KMP plugins are declared `apply false` here so they resolve once and the
// version catalog stays the single source of truth for plugin versions.

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
}
