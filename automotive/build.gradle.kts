plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "ae.sayyara.automotive"
    compileSdk = 36
    defaultConfig {
        applicationId = "ae.sayyara.app" // same package as phone app; separate AAOS APK/track
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"
    }
    useLibrary("android.car")
    signingConfigs {
        // Release key from CI secrets (base64 keystore decoded to SAYYARA_KEYSTORE_FILE).
        // Without it, release builds fall back to the debug key: fine for sideloading, not for Play.
        val ks = System.getenv("SAYYARA_KEYSTORE_FILE")
        if (ks != null && file(ks).exists()) {
            create("release") {
                storeFile = file(ks)
                storePassword = System.getenv("SAYYARA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SAYYARA_KEY_ALIAS")
                keyPassword = System.getenv("SAYYARA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime") }
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-automotive:1.7.0")
}
