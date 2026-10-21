plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.lixiaolin94.dialkit.sample.compose"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.lixiaolin94.dialkit.sample.compose"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Installable release (debug key) to check the no-op path: same code, no overlay.
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Debug gets the overlay, release the no-op with the same API; the Compose adapter works with both.
    implementation(project(":dialkit-compose"))
    debugImplementation(project(":dialkit"))
    releaseImplementation(project(":dialkit-noop"))
    implementation(libs.kotlin.stdlib)

    // Accessibility checks through UiAutomation, the way a screen reader sees the overlay (debug build only).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
}
