// Versions are pinned to a toolchain that builds with JDK 17 and Android SDK platform 33.
// See README.md "Toolchain" before upgrading.
plugins {
    id("com.android.application") version "8.0.0" apply false
    id("org.jetbrains.kotlin.android") version "1.8.0" apply false
}
