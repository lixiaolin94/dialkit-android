plugins {
    alias(libs.plugins.dialkit.library)
    alias(libs.plugins.maven.publish)
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "io.github.lixiaolin94.dialkit"
    // Shared with :dialkit-noop: the DSL, value cells and model (no overlay, store or UI).
    sourceSets.getByName("main").kotlin.directories.add("src/shared/java")
    // Device tests (src/androidTest): store lifecycle, persistence, in-page panels, floatingPanel = false.
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

dependencies {
    // The only runtime dependency is the Kotlin standard library (which every Kotlin host has).
    implementation(libs.kotlin.stdlib)
    // Compile-time only: annotations, and the Lifecycle overloads for hosts that already use androidx.lifecycle.
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.androidx.lifecycle.common)

    testImplementation(libs.junit)
    // android.jar's org.json is stubbed in local unit tests; the real implementation for the import tests.
    testImplementation(libs.org.json)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}
