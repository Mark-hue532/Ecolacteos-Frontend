// `androidApp` es un módulo Android hoja normal (`com.android.application` + `org.jetbrains.kotlin.android`),
// NO un módulo Kotlin Multiplatform -- `shared/` es el único módulo KMP y `androidApp` lo consume como una
// dependencia de proyecto normal. Ver `gradle.properties` (`android.newDsl=false`, `android.builtInKotlin=false`)
// para el porqué de este plugin explícito en vez del Kotlin incorporado de AGP 9.x.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    // Fase 7 (`PROMPT_FASE_07.md §6`): `MainActivity.kt` monta `setContent { App() }`, código composable
    // propio de este módulo -- necesita su propio compilador de Compose, no solo el de `shared/`.
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.ecolacteos.acopio"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ecolacteos.acopio"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-fase1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    // `shared` declara koin-core como `implementation`, no `api` (CLAUDE.md §3.4 -- Koin es un detalle de
    // implementación de `shared/`), así que no llega transitivamente. `MainActivity` necesita `module {}`
    // para registrar el binding de `SecureTokenStorage`, específico de esta plataforma (Fase 3).
    implementation(libs.koin.core)
    // Fase 7: `shared` expone `compose.runtime`/`compose.ui` como `api` (ver `shared/build.gradle.kts`),
    // pero `ComponentActivity.setContent` en sí vive en `activity-compose`, que `androidApp` sí debe
    // declarar directo -- es el punto de montaje de la plataforma, no un detalle interno de `shared/`.
    implementation(libs.androidx.activity.compose)
}
