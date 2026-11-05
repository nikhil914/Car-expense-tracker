// Root build used when an Android SDK is present (see settings.gradle.kts).
// All plugins declared once so AGP and the Kotlin plugins share one classloader.
plugins {
    kotlin("multiplatform") apply false
    kotlin("android") apply false
    kotlin("plugin.serialization") apply false
    kotlin("plugin.compose") apply false
    id("com.android.application") apply false
    id("com.google.devtools.ksp") apply false
}
