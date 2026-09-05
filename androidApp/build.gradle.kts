// `androidApp` es un módulo Android hoja normal (`com.android.application` + `org.jetbrains.kotlin.android`),
// NO un módulo Kotlin Multiplatform -- `shared/` es el único módulo KMP y `androidApp` lo consume como una
// dependencia de proyecto normal. Ver `gradle.properties` (`android.newDsl=false`, `android.builtInKotlin=false`)
// para el porqué de este plugin explícito en vez del Kotlin incorporado de AGP 9.x.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
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
}

dependencies {
    implementation(project(":shared"))
}
