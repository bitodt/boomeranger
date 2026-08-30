plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val resolvedVersionName = providers.gradleProperty("versionName")
    .orElse(providers.gradleProperty("app.versionName"))
    .get()
val resolvedVersionCode = providers.gradleProperty("versionCode")
    .orElse(providers.gradleProperty("app.versionCode"))
    .get()
    .toInt()

android {
    namespace = "com.boomeranger.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.boomeranger.app"
        minSdk = 26
        targetSdk = 35
        // GitHub Releases pass -PversionName / -PversionCode from the tag.
        // Local and PR CI builds use app.versionName / app.versionCode in gradle.properties.
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Shared debug keystore so CI artifacts, local installs, and GitHub Release
    // APKs share one signing identity. Without this, each GitHub Actions runner
    // generates a fresh debug keystore and Android rejects updates with
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE.
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/boomeranger-debug.jks")
            storePassword = "android"
            keyAlias = "boomeranger-debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Sideload GitHub Releases. Same cert as debug so Dev and stable
            // builds are both installable from CI; they stay separate apps
            // because debug uses applicationIdSuffix. Not for Play Store.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val media3 = "1.5.1"

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-common:$media3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
