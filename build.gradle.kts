// Versions are pinned to a toolchain that builds with JDK 17 and Android SDK platform 36.
// See README.md "Toolchain" before upgrading.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    // Kotlin 2 ships the Compose compiler as a Kotlin plugin, released with Kotlin itself
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
