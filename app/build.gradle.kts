plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.blockreels"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.blockreels"
        // 31 rather than 30 so dynamic colour is available unconditionally. Android 12 is
        // a safe floor for a personal sideloaded app.
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        // CI appends the short commit sha, so the app can show exactly which build is
        // installed — otherwise an update that silently didn't take looks identical to one
        // that did.
        versionName = "0.1.0" + (System.getenv("BUILD_TAG")?.let { "+$it" } ?: "")
    }

    // Without this, AGP invents a debug keystore at ~/.android/debug.keystore on first use.
    // CI runners are ephemeral, so every build got a *different* random key and every APK
    // refused to install over the last one ("App not installed"), forcing an uninstall and
    // a full re-grant of accessibility and restricted settings each time.
    //
    // A checked-in debug key is the usual fix and makes every build interchangeable. It is
    // deliberately debug-only and never used for anything trusted: the repo is public, so
    // treat the key as known to everyone and never reuse it to sign a real release.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/blockreels-debug.jks")
            storePassword = "android"
            keyAlias = "blockreels"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Dump mode ships in release on purpose: when an app update breaks a
            // detector you want to re-diff on the phone, not on a laptop you don't have.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
