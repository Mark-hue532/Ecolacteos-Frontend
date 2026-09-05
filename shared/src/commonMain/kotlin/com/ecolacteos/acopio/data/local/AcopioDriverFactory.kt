package com.ecolacteos.acopio.data.local

import app.cash.sqldelight.db.SqlDriver

/** Nombre del archivo de SQLite en disco (Android/iOS reales) -- visible en todo el módulo. */
internal const val NOMBRE_BASE_DE_DATOS = "acopio.db"

/**
 * Crea el [SqlDriver] real de SQLite por plataforma (`PROMPT_FASE_04.md §1`). Sin constructor común a
 * propósito -- Android necesita un `Context` real, iOS no necesita nada -- mismo patrón que
 * `security/SecureTokenStorage.kt` (Fase 3): `commonMain` nunca instancia esto directamente, lo arma el
 * módulo de Koin de cada plataforma cuando exista un consumidor real (Fase 6, Repository -- todavía no
 * existe, este `expect`/`actual` queda sin wiring de DI a propósito, ver checkpoint).
 *
 * El target `jvm()` tiene su propio `actual` solo para que `:shared:jvmTest` compile
 * (`MOBILE_ARCHITECTURE.md §14`, "Testing"); nunca se empaqueta en producción. Los tests de esta fase usan
 * en cambio `crearDriverDeTest()` (`commonTest`), no esta clase -- esa es la que de verdad se ejecuta en
 * CI sobre los tres targets.
 */
expect class AcopioDriverFactory {
    fun crearDriver(): SqlDriver
}
