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
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        // Los tres targets iOS comparten el mismo engine Darwin -- ver KT-* de Kotlin/Native, el source
        // set intermedio "iosMain" ya lo crea por default el plugin KMP al declarar iosX64/iosArm64/
        // iosSimulatorArm64 (todos "ios.main" agrupan bajo iosMain automáticamente).
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            // Motor real solo para que jvm() tenga uno disponible (MOBILE_ARCHITECTURE.md §14) -- los
            // tests de esta fase usan MockEngine (ktor-client-mock), no CIO real.
            implementation(libs.ktor.client.cio)
        }
        // Test real del Keystore (PROMPT_FASE_03.md §8) -- necesita un dispositivo/emulador que el CI
        // actual no tiene (`verificacion-android.yml` no levanta uno). Se declara y se documenta cómo
        // correrlo a mano en el checkpoint de la Fase 3; queda sin ejecutar en CI, no se declara verificado.
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.junit)
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
        // Requerido para que `androidInstrumentedTest` (Fase 3, test real de Keystore) tenga runner --
        // sin esto `connectedAndroidTest`/`connectedDebugAndroidTest` ni se generan.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
