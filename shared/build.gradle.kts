import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)

    // Los targets se declaran siempre, sin condicionales por host -- iOS no compila en Windows
    // pero commonMain debe seguir siendo multiplataforma correcto (CLAUDE.md §8, ver también §9 de esta fase).
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm() // permite correr commonTest en JVM puro sin emulador/simulador (ver MOBILE_ARCHITECTURE.md §14 -- Testing)

    // `iosX64()` (Mac Intel) se retiró en la Fase 7 (`PROMPT_FASE_07.md §6`, checkpoint): Compose
    // Multiplatform 1.12.0 no publica variante `iosX64` para ningún artefacto de `org.jetbrains.compose.*`
    // (confirmado contra Maven Central -- solo `iosArm64`/`iosSimulatorArm64`), baja real de upstream
    // (Apple discontinuó Macs Intel; Kotlin/Native viene deprecando ese target), no un problema de versión.
    // `verificacion-ios.yml` corre en `macos-14` (Apple Silicon) y solo ejecutó siempre
    // `iosSimulatorArm64Test` -- nunca dependió de `iosX64`, así que este cambio no reduce cobertura de CI.
    listOf(
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
            // El plugin de SQLDelight ya agrega `runtime` automáticamente a commonMain -- se declara acá
            // de todos modos por explicitud (mismo criterio que las demás líneas de este bloque). Flow
            // reactivo de `observarTodos(...)` (Query.asFlow()/mapToList) necesita el artefacto aparte
            // `coroutines-extensions`, que el plugin no agrega solo.
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)

            // Compose Multiplatform (Fase 7, `PROMPT_FASE_07.md §6`). `api`, no `implementation`: el
            // `@Composable fun App()` raíz de `shared/ui/` y su punto de montaje en `androidApp/MainActivity`
            // (`setContent { App() }`) necesitan `compose.runtime` en el classpath de compilación de
            // `androidApp` -- a diferencia de Koin/Ktor (CLAUDE.md §3.4), Compose no es un detalle interno
            // de `shared/` que la UI deba dejar de ver.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.navigation.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
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
            implementation(libs.sqldelight.android.driver)
        }
        // Los tres targets iOS comparten el mismo engine Darwin -- ver KT-* de Kotlin/Native, el source
        // set intermedio "iosMain" ya lo crea por default el plugin KMP al declarar iosX64/iosArm64/
        // iosSimulatorArm64 (todos "ios.main" agrupan bajo iosMain automáticamente).
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        jvmMain.dependencies {
            // Motor real solo para que jvm() tenga uno disponible (MOBILE_ARCHITECTURE.md §14) -- los
            // tests de esta fase usan MockEngine (ktor-client-mock), no CIO real.
            implementation(libs.ktor.client.cio)
            // Driver JDBC en memoria solo para que el `actual AcopioDriverFactory` de este target compile
            // y `:shared:jvmTest` corra sin emulador/simulador -- nunca se empaqueta en producción, ver
            // `AcopioDriverFactory.jvm.kt` (mismo criterio que `SecureTokenStorage.jvm.kt` de Fase 3).
            implementation(libs.sqldelight.sqlite.driver)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
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
        // Unit test JVM-based de Android (sin emulador, `verificacion-android.yml`) -- corre el mismo
        // `commonTest` de esta fase (`PROMPT_FASE_04.md §7`). JDBC en memoria, igual que jvmTest: un
        // `androidUnitTest` corre sobre el JVM del host, sin `Context` real, así que no puede usar
        // `AndroidSqliteDriver` (ese sí necesita un dispositivo/emulador -- fuera de alcance, ver
        // "No es criterio de esta fase" del prompt).
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
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

// PROMPT_FASE_04.md §1: los .sq viven en un source set propio, no en kotlin/. `packageName` fija la ruta
// exacta -- shared/src/commonMain/sqldelight/com/ecolacteos/acopio/data/local/*.sq (un directorio por
// segmento del package, como en cualquier fuente Kotlin/Java). Clase generada: `AcopioDatabase`.
sqldelight {
    databases {
        create("AcopioDatabase") {
            packageName.set("com.ecolacteos.acopio.data.local")
        }
    }
}
