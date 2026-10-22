plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.lixiaolin94.dialkit.sample.views"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.lixiaolin94.dialkit.sample.views"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.2"
    }

    buildTypes {
        // Minified release, signed with the debug key, to measure what DialKit costs a View-only app.
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        // Release settings (R8, names kept) with the full DialKit, to measure its cost in a View-only app.
        create("measure") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(project(":dialkit"))
    releaseImplementation(project(":dialkit-noop"))
    "measureImplementation"(project(":dialkit"))
    implementation(libs.kotlin.stdlib)
}
