// Root build for environments without an Android SDK (core module only).
// With an SDK, settings.gradle.kts switches to build.android.gradle.kts.
plugins {
    kotlin("multiplatform") apply false
    kotlin("plugin.serialization") apply false
}
