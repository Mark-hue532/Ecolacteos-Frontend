package com.ecolacteos.acopio.domain

/**
 * Punto de indirección para la política de logout bloqueado por trabajo sin sincronizar (C-09,
 * `MOBILE_ARCHITECTURE.md §4`, `MOBILE_SCREENS.md §4 S-07`). Las tablas `*_local` que esto necesita
 * consultar son de la Fase 4, y la implementación real sobre SQLite es de la Fase 6 -- mismo patrón que
 * `TokenProvider` de la Fase 2: la interfaz se escribe y se consume ahora, la implementación real llega
 * después y solo hay que reemplazar el binding de Koin.
 */
interface VerificadorPendientes {
    /** `true` si hay filas `PENDING`, `PENDING_DEPENDENCY`, `SYNCING` o `FAILED` en cualquier tabla `*_local`. */
    suspend fun hayTrabajoSinSincronizar(): Boolean
}

/**
 * Default de Koin hasta que la Fase 6 provea la implementación real: sin tablas locales todavía (Fase 4),
 * nunca hay nada que bloquee el logout.
 */
class VerificadorPendientesSinImplementar : VerificadorPendientes {
    override suspend fun hayTrabajoSinSincronizar(): Boolean = false
}
