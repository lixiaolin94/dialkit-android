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
        versionName = "0.0.1"
    }

    buildTypes {
        // Installable release (debug key): the same screens on dialkit-noop, where the tuning screens are empty.
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
    debugImplementation(project(":dialkit"))
    releaseImplementation(project(":dialkit-noop"))
    implementation(libs.kotlin.stdlib)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
}
