pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Autoprovisiona el JDK 17 (toolchain) si el host no lo tiene instalado -- el equipo desarrolla en
    // Windows y no todos los puestos tienen JDK 17 configurado como default. Ver CLAUDE.md §4.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "acopio-mobile"

include(":shared")
include(":androidApp")
