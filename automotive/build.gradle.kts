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
        versionCode = 1
        versionName = "0.1.0"
    }
    useLibrary("android.car")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
