plugins {
    alias(libs.plugins.dialkit.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.lixiaolin94.dialkit.compose"
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    // The host picks the implementation per variant: dialkit (debug) or dialkit-noop (release).
    compileOnly(project(":dialkit"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.animation.core)
    // AndroidView, for DialKitPanel (the in-page panel).
    implementation(libs.androidx.compose.ui)
}
