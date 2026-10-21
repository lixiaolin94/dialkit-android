plugins {
    alias(libs.plugins.dialkit.library)
    alias(libs.plugins.maven.publish)
}

// Same public API as :dialkit without overlay, store or UI: dials keep their declared defaults.
// Hosts use debugImplementation(dialkit) + releaseImplementation(dialkit-noop).
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "io.github.lixiaolin94.dialkit.noop"
    sourceSets.getByName("main").kotlin.directories.add("../dialkit/src/shared/java")
}

dependencies {
    implementation(libs.kotlin.stdlib)
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.androidx.lifecycle.common)
}
