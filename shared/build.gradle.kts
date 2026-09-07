import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi

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

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->

        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }

        iosTarget.binaries.all {
            @OptIn(KotlinNativeCacheApi::class)
            disableNativeCache(
                version = DisableCacheInKotlinVersion.`2_4_10`,
                reason = "navigation-common 2.9.0-alpha16: RouteDecoder duplicado al construir el cache del klib",
            )
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

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)

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

            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.zxing.core)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.sqlite.driver)
        }

        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }

        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.junit)
        }

        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED
        )
    }
}

// PROMPT_FASE_08E.md §1.2 -- una coma dentro de un nombre de test entre backticks rompe
// compileTestKotlinIosArm64/IosSimulatorArm64 con "Name contains illegal characters: ','". Pasó 4 veces
// (Fases 2, 6, 8C, 8D), siempre descubierto después de una compilación de iOS que tarda minutos. Este check
// corre en segundos, ANTES de esa compilación (ver el `dependsOn` más abajo), y falla con el archivo y el
// nombre exactos en vez de dejar que Kotlin/Native lo reporte tarde y sin contexto.
val verificarNombresDeTestSinComa by tasks.registering {
    group = "verification"
    description = "Falla si un nombre de test entre backticks en commonTest contiene una coma (§1.2)."
    val directorioCommonTest = layout.projectDirectory.dir("src/commonTest/kotlin").asFile
    inputs.dir(directorioCommonTest)
    outputs.upToDateWhen { true } // no produce artefacto -- solo valida; re-ejecuta si cambian los inputs.
    doLast {
        val patronNombreDeTest = Regex("""fun\s+`([^`]*)`""")
        val ofensores = directorioCommonTest.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { archivo ->
                patronNombreDeTest.findAll(archivo.readText())
                    .map { it.groupValues[1] }
                    .filter { nombre -> nombre.contains(',') }
                    .map { nombre -> "${archivo.relativeTo(directorioCommonTest)}: `$nombre`" }
            }
            .toList()
        check(ofensores.isEmpty()) {
            "Nombres de test con coma entre backticks (rompen compileTestKotlinIosArm64 -- PROMPT_FASE_08E.md §1.2):\n" +
                ofensores.joinToString("\n") { "  - $it" }
        }
    }
}

tasks.matching { it.name == "compileTestKotlinIosArm64" || it.name == "compileTestKotlinIosSimulatorArm64" }
    .configureEach { dependsOn(verificarNombresDeTestSinComa) }

android {
    namespace = "com.ecolacteos.acopio.shared"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("AcopioDatabase") {
            packageName.set("com.ecolacteos.acopio.data.local")
        }
    }
}
