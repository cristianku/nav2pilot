import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.github.triplet.play")
}

// A tag v1.2.3 produces versionName 1.2.3 and versionCode 1002003.
// Keep local debug builds independent of release credentials.
val releaseVersion = providers.gradleProperty("releaseVersion").orNull
val releaseVersionCode = releaseVersion?.let { version ->
    val match = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)(?:\.(0|[1-9]\d*))?""").matchEntire(version)
        ?: throw GradleException("releaseVersion must be MAJOR.MINOR or MAJOR.MINOR.PATCH (without v)")
    val major = match.groupValues[1].toLongOrNull()
    val minor = match.groupValues[2].toLongOrNull()
    val patch = match.groupValues[3].ifEmpty { "0" }.toLongOrNull()
    require(major != null && major in 0..2100 && minor != null && minor in 0..999 &&
        patch != null && patch in 0..999) {
        "Release version components are out of range (major <= 2100, minor/patch <= 999)"
    }
    val code = major * 1_000_000 + minor * 1_000 + patch
    require(code in 1..2_100_000_000) { "Release versionCode must be between 1 and 2100000000" }
    code.toInt()
}

val localSigning = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
}
fun signingValue(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }
        ?: localSigning.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")

android {
    namespace = "com.cristianku.nav2pilot"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cristianku.nav2pilot"
        // Android 8.0: older phones and car boxes (CarlinKit) keep working with the same APK
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode ?: 1
        versionName = releaseVersion ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile?.let { rootProject.file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // R8: shrinks and obfuscates the code (Google Play warns below 25% obfuscation) and drops unused resources
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }

    lint {
        abortOnError = true
        // The toolchain is pinned on purpose (README "Toolchain"); these only report newer versions.
        disable += "OldTargetApi"
        disable += "GradleDependency"
        disable += "AndroidGradlePluginVersion"
    }
}

// Google Play upload with the service account key in .signing/ (ignored by git, see README).
// The track can be changed from the command line: ./gradlew publishReleaseBundle --track alpha
play {
    serviceAccountCredentials.set(rootProject.file(".signing/play-service-account.json"))
    track.set("internal")
    defaultToAppBundles.set(true)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Never let a release build silently fall back to an unsigned APK or the debug key.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(releaseVersion != null) { "Release builds require -PreleaseVersion=MAJOR.MINOR.PATCH" }
        check(listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }) {
            "Release signing is missing: configure ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
                "ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD, or local keystore.properties (see README)"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Full-screen map: MapLibre, the open source fork of the Mapbox map engine, on Maven Central with no token.
    // Base map from OpenFreeMap (free, no key), or Mapbox Streets raster tiles with the user's own public token,
    // so that Mapbox routes are shown on a Mapbox map.
    implementation("org.maplibre.gl:android-sdk:13.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // Real org.json for JVM unit tests (android.jar only ships stubs).
    testImplementation("org.json:json:20230618")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
