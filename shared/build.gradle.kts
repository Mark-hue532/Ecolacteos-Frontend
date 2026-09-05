import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    jvmToolchain(17)

    // Los cuatro targets se declaran siempre, sin condicionales por host -- iOS no compila en Windows
    // pero commonMain debe seguir siendo multiplataforma correcto (CLAUDE.md §8, ver también §9 de esta fase).
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm() // permite correr commonTest en JVM puro sin emulador/simulador (ver MOBILE_ARCHITECTURE.md §14 -- Testing)

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.bignum)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
            implementation(libs.turbine)
        }
    }
}

// Sin esto, la consola de CI solo muestra "> Task :shared:xxxTest / BUILD SUCCESSFUL" sin nombres de test
// individuales -- suficiente para confiar en el build, pero no para verificar a simple vista en el log del
// runner que un test puntual (p.ej. el roundtrip decimal de DATA-002) corrió y pasó en el target nativo iOS.
tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
    }
}

android {
    namespace = "com.ecolacteos.acopio.shared"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
