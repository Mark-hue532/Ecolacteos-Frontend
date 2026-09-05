package com.ecolacteos.acopio.di

import org.koin.core.context.startKoin
import org.koin.core.error.KoinApplicationAlreadyStartedException
import org.koin.dsl.KoinAppDeclaration

/**
 * Arranque único del grafo de Koin, invocable desde `androidApp` (en `MainActivity`, hasta que exista una
 * `Application` propia) e `iosApp` (al lanzar la app). [appDeclaration] permite que cada plataforma agregue
 * configuración o módulos propios (ej. `androidContext()` cuando se incorpore `koin-android` en una fase
 * posterior) sin cambiar esta firma.
 *
 * Devuelve `Unit` a propósito, no el `KoinApplication` de `startKoin` -- Koin es un detalle de
 * implementación de `shared/` (declarado `implementation`, no `api`, ver `shared/build.gradle.kts`);
 * exponer su tipo de retorno obligaría a `androidApp`/`iosApp` a tener `koin-core` en su propio classpath
 * de compilación solo para poder llamar a esta función (CLAUDE.md §3.4).
 *
 * Idempotente a propósito: si el proceso ya tiene un grafo de Koin activo (ej. `MainActivity` relanzada
 * por el sistema con el proceso todavía vivo -- pasa de verdad, se reprodujo en un emulador durante esta
 * fase) `startKoin` lanza `KoinApplicationAlreadyStartedException`; acá se ignora en vez de tirar la app abajo.
 * `GlobalContext.getOrNull()` (la forma más directa de chequear esto) no está disponible en `commonMain`
 * -- solo en los `actual` por plataforma -- así que capturar la excepción es la forma portable de lograr
 * el mismo efecto.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    try {
        startKoin {
            appDeclaration()
            modules(coreModule, networkModule)
        }
    } catch (yaIniciado: KoinApplicationAlreadyStartedException) {
        // No-op: el grafo ya existe en este proceso, no hay nada que rehacer.
    }
}
