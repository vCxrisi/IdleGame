plugins {
    // Plugins werden hier nur einmal geladen, damit Unterprojekte sie nicht mehrfach in den Classpath laden.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}
