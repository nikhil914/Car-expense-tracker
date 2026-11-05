pluginManagement {
    repositories { maven("https://maven.google.com"); gradlePluginPortal(); mavenCentral() }
    // Versions declared here are resolved only when a module applies the plugin,
    // so :core builds without the Android Gradle Plugin / SDK.
    plugins {
        kotlin("multiplatform") version "2.2.20"
        kotlin("android") version "2.2.20"
        kotlin("plugin.serialization") version "2.2.20"
        kotlin("plugin.compose") version "2.2.20"
        id("com.android.application") version "8.13.0"
        id("com.google.devtools.ksp") version "2.2.20-2.0.4"
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral(); maven("https://maven.google.com") }
}
rootProject.name = "sayyara"
include(":core")

// Android modules need an Android SDK (ANDROID_HOME or local.properties sdk.dir).
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    file("local.properties").takeIf { it.exists() }?.readText()?.contains("sdk.dir") == true
if (hasAndroidSdk) {
    include(":app")        // phone app + Android Auto (Car App Library)
    include(":automotive") // Android Automotive OS (built-in head unit)
}
rootProject.buildFileName = if (hasAndroidSdk) "build.android.gradle.kts" else "build.gradle.kts"
