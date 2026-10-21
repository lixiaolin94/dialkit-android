import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * What every published DialKit module shares: the consumer floor (minSdk 23, Java 11 bytecode, Kotlin 2.0
 * metadata), strict explicit API for main sources, the `dialkit_` resource prefix and lint rules.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = 35
            resourcePrefix = "dialkit_"
            defaultConfig {
                minSdk = 23
                consumerProguardFiles("consumer-rules.pro")
                // AGP 9 defaults this to compileSdk. DialKit references API 34 only through compile-time constants
                // (haptics, guarded by SDK_INT), so hosts compiling against 33 are fine.
                aarMetadata { minCompileSdk = 33 }
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
            lint {
                warningsAsErrors = false
                abortOnError = true
                checkDependencies = false
                disable += setOf("GradleDependency", "AndroidGradlePluginVersion") // toolchain pinned on purpose (docs/DESIGN.md §2)
            }
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
                // Metadata readable by any Kotlin 2.x host.
                languageVersion.set(KotlinVersion.KOTLIN_2_0)
                apiVersion.set(KotlinVersion.KOTLIN_2_0)
                if ("Test" !in name) freeCompilerArgs.add("-Xexplicit-api=strict")
            }
        }
    }
}
