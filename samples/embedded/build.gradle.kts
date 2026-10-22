plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.lixiaolin94.dialkit.embedded"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.lixiaolin94.dialkit.embedded"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.2"
    }

    buildTypes {
        // Installable release (debug key). The tuning screens are part of this app, so release keeps them.
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
    implementation(project(":dialkit-compose"))
    // Tuning is a feature of this app, not a debug tool: the full library in every build type. With
    // floatingPanel = false nothing floats in release either; only the app's own Tuning screens show panels.
    implementation(project(":dialkit"))
    implementation(libs.kotlin.stdlib)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
}
