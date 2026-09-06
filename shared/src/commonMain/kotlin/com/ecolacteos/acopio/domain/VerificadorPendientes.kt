package com.ecolacteos.acopio.domain

/**
 * Punto de indirección para la política de logout bloqueado por trabajo sin sincronizar (C-09,
 * `MOBILE_ARCHITECTURE.md §4`, `MOBILE_SCREENS.md §4 S-07`). Implementada por
 * `domain/usecase/VerificarPendientesUseCase.kt` (Fase 6) contra las 4 tablas `*_local` -- el stub de Fase
 * 3 (`VerificadorPendientesSinImplementar`) ya no existe, ver `di/UseCaseModule.kt`.
 */
interface VerificadorPendientes {
    /** `true` si hay filas `PENDING`, `PENDING_DEPENDENCY`, `SYNCING` o `FAILED` en cualquier tabla `*_local`. */
    suspend fun hayTrabajoSinSincronizar(): Boolean
}
