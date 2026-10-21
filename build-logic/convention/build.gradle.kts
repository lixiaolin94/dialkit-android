plugins {
    `kotlin-dsl`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    // Provided at runtime by the main build's classpath (declared there with `apply false`).
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "dialkit.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
