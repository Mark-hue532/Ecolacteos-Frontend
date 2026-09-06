package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.model.SyncStatus
import kotlinx.datetime.LocalDateTime

/**
 * Único lugar que traduce el `SyncStatus` persistido (Fase 4) al [EstadoSincronizacion] de dominio
 * (`PROMPT_FASE_06.md §7`) -- los 4 `ObservarPendientesUseCase`/similares lo reusan, ninguno repite el
 * `when`. [reintentable] en `Fallido` se calcula de `nextAttemptAt`: si el Sync Engine todavía tiene
 * programado un reintento automático, no requiere acción del usuario (aunque `ReintentarManualUseCase`
 * pueda igual forzarlo antes de tiempo).
 */
internal fun estadoSincronizacionDe(
    status: SyncStatus,
    syncError: String?,
    nextAttemptAt: LocalDateTime?,
): EstadoSincronizacion = when (status) {
    SyncStatus.PENDING -> EstadoSincronizacion.Pendiente
    SyncStatus.PENDING_DEPENDENCY -> EstadoSincronizacion.EsperandoDependencia(motivoConocido = syncError)
    SyncStatus.SYNCING -> EstadoSincronizacion.Sincronizando
    SyncStatus.SYNCED -> EstadoSincronizacion.Sincronizado
    SyncStatus.FAILED -> EstadoSincronizacion.Fallido(
        motivo = syncError ?: "Error desconocido",
        reintentable = nextAttemptAt != null,
    )
}
