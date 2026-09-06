package com.ecolacteos.acopio.data.local.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ecolacteos.acopio.core.DispatcherProvider
import com.ecolacteos.acopio.data.local.Analisis_calidad_local
import com.ecolacteos.acopio.data.local.AnalisisCalidadLocalQueries
import com.ecolacteos.acopio.domain.model.AnalisisCalidad
import com.ecolacteos.acopio.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime

/** Local Data Source de `analisis_calidad_local` (`PROMPT_FASE_04.md §6`). */
class AnalisisCalidadLocalDataSource(
    private val queries: AnalisisCalidadLocalQueries,
    private val dispatchers: DispatcherProvider,
) {
    fun insertar(analisis: AnalisisCalidad) {
        queries.insertar(
            uuidCliente = analisis.uuidCliente,
            serverId = analisis.serverId,
            usuarioId = analisis.usuarioId,
            registroAcopioUuidCliente = analisis.registroAcopioUuidCliente,
            registroAcopioServerId = analisis.registroAcopioServerId,
            folioMuestra = analisis.folioMuestra,
            agua = analisis.agua,
            proteina = analisis.proteina,
            lactosa = analisis.lactosa,
            densidad = analisis.densidad,
            temperatura = analisis.temperatura,
            ph = analisis.ph,
            aguaAnadida = analisis.aguaAnadida,
            syncStatus = analisis.syncStatus,
            syncAttempts = analisis.syncAttempts,
            syncError = analisis.syncError,
            nextAttemptAt = analisis.nextAttemptAt,
            creadoEn = analisis.creadoEn,
            sincronizadoEn = analisis.sincronizadoEn,
        )
    }

    fun obtenerPorUuidCliente(uuidCliente: String): AnalisisCalidad? =
        queries.obtenerPorUuidCliente(uuidCliente).executeAsOneOrNull()?.aDominio()

    fun obtenerPendientes(usuarioId: String, ahora: LocalDateTime): List<AnalisisCalidad> =
        queries.obtenerPendientes(usuarioId, ahora).executeAsList().map { it.aDominio() }

    /**
     * Filas retenidas en `PENDING_DEPENDENCY` (C-03, §18.1) -- **no** son candidatas a enviar; el Sync
     * Engine las reevalúa al final de cada ciclo por si el padre ya resolvió su `server_id`.
     */
    fun obtenerEnEsperaDeDependencia(usuarioId: String): List<AnalisisCalidad> =
        queries.obtenerEnEsperaDeDependencia(usuarioId).executeAsList().map { it.aDominio() }

    fun observarTodos(usuarioId: String): Flow<List<AnalisisCalidad>> =
        queries.observarTodos(usuarioId).asFlow().mapToList(dispatchers.io)
            .map { filas -> filas.map { it.aDominio() } }

    fun actualizarEstadoSync(
        uuidCliente: String,
        status: SyncStatus,
        syncAttempts: Int,
        syncError: String?,
        nextAttemptAt: LocalDateTime?,
    ) {
        queries.actualizarEstadoSync(
            syncStatus = status,
            syncAttempts = syncAttempts,
            syncError = syncError,
            nextAttemptAt = nextAttemptAt,
            uuidCliente = uuidCliente,
        )
    }

    /** Ver [RegistroAcopioLocalDataSource.marcarSincronizado] -- `serverId` nullable por `DATA-014`. */
    fun marcarSincronizado(uuidCliente: String, serverId: String?, sincronizadoEn: LocalDateTime) {
        queries.marcarSincronizado(serverId = serverId, sincronizadoEn = sincronizadoEn, uuidCliente = uuidCliente)
    }

    fun eliminarSincronizadosAntesDe(fecha: LocalDateTime) {
        queries.eliminarSincronizadosAntesDe(fecha)
    }
}

private fun Analisis_calidad_local.aDominio(): AnalisisCalidad = AnalisisCalidad(
    uuidCliente = uuid_cliente,
    serverId = server_id,
    usuarioId = usuario_id,
    registroAcopioUuidCliente = registro_acopio_uuid_cliente,
    registroAcopioServerId = registro_acopio_server_id,
    folioMuestra = folio_muestra,
    agua = agua,
    proteina = proteina,
    lactosa = lactosa,
    densidad = densidad,
    temperatura = temperatura,
    ph = ph,
    aguaAnadida = agua_anadida,
    syncStatus = sync_status,
    syncAttempts = sync_attempts,
    syncError = sync_error,
    nextAttemptAt = next_attempt_at,
    creadoEn = creado_en,
    sincronizadoEn = sincronizado_en,
)
