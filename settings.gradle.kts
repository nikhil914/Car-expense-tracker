pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral(); maven("https://maven.google.com") }
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
