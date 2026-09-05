package com.ecolacteos.acopio.domain.model

/**
 * Ciclo de vida de sincronización de las 4 tablas `*_local` (`MOBILE_ARCHITECTURE.md §11.1`). Sin
 * contraparte remota -- lo escribe y lee solo esta app, nunca viaja en un DTO de red (`CLAUDE.md §3.1` no
 * aplica acá porque no es un campo del contrato, pero el mismo criterio de "no inventar" sí: estos 5
 * valores son exactamente los que declara §11.1, ni uno más).
 *
 * `PENDING_DEPENDENCY` (C-03) solo lo asigna `AnalisisCalidad`/`LoteProduccion` -- existe acá por
 * uniformidad del enum, pero `RegistroAcopio`/`Venta` nunca lo usan (esos dos recursos no dependen de
 * ningún id ajeno).
 */
enum class SyncStatus {
    PENDING,
    PENDING_DEPENDENCY,
    SYNCING,
    SYNCED,
    FAILED,
}
