package com.ecolacteos.acopio.data.local.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ecolacteos.acopio.core.DispatcherProvider
import com.ecolacteos.acopio.data.local.Lote_produccion_local
import com.ecolacteos.acopio.data.local.LoteProduccionLocalQueries
import com.ecolacteos.acopio.data.local.Lote_produccion_registro_local
import com.ecolacteos.acopio.data.local.LoteProduccionRegistroLocalQueries
import com.ecolacteos.acopio.domain.model.LoteProduccion
import com.ecolacteos.acopio.domain.model.LoteProduccionRegistro
import com.ecolacteos.acopio.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime

/**
 * Local Data Source de `lote_produccion_local` **y** `lote_produccion_registro_local`
 * (`PROMPT_FASE_04.md §6`): una sola clase para las dos tablas -- la segunda no tiene ciclo de vida propio,
 * es espejo N:M de la primera (trampa #3: nunca lleva `sync_status`).
 */
class LoteProduccionLocalDataSource(
    private val queries: LoteProduccionLocalQueries,
    private val registroQueries: LoteProduccionRegistroLocalQueries,
    private val dispatchers: DispatcherProvider,
) {
    fun insertar(lote: LoteProduccion) {
        queries.insertar(
            uuidCliente = lote.uuidCliente,
            serverId = lote.serverId,
            usuarioId = lote.usuarioId,
            fecha = lote.fecha,
            tipoQuesoId = lote.tipoQuesoId,
            litrosUsados = lote.litrosUsados,
            unidadesObtenidas = lote.unidadesObtenidas,
            syncStatus = lote.syncStatus,
            syncAttempts = lote.syncAttempts,
            syncError = lote.syncError,
            nextAttemptAt = lote.nextAttemptAt,
            creadoEn = lote.creadoEn,
            sincronizadoEn = lote.sincronizadoEn,
        )
    }

    fun insertarRegistro(registro: LoteProduccionRegistro) {
        registroQueries.insertar(
            loteUuidCliente = registro.loteUuidCliente,
            registroAcopioUuidCliente = registro.registroAcopioUuidCliente,
            registroAcopioServerId = registro.registroAcopioServerId,
        )
    }

    fun obtenerRegistrosPorLote(loteUuidCliente: String): List<LoteProduccionRegistro> =
        registroQueries.obtenerPorLote(loteUuidCliente).executeAsList().map { it.aDominio() }

    fun eliminarRegistrosPorLote(loteUuidCliente: String) {
        registroQueries.eliminarPorLote(loteUuidCliente)
    }

    fun obtenerPorUuidCliente(uuidCliente: String): LoteProduccion? =
        queries.obtenerPorUuidCliente(uuidCliente).executeAsOneOrNull()?.aDominio()

    fun obtenerPendientes(usuarioId: String, ahora: LocalDateTime): List<LoteProduccion> =
        queries.obtenerPendientes(usuarioId, ahora).executeAsList().map { it.aDominio() }

    /** Ver [AnalisisCalidadLocalDataSource.obtenerEnEsperaDeDependencia] -- mismo criterio (C-03, §18.1). */
    fun obtenerEnEsperaDeDependencia(usuarioId: String): List<LoteProduccion> =
        queries.obtenerEnEsperaDeDependencia(usuarioId).executeAsList().map { it.aDominio() }

    fun observarTodos(usuarioId: String): Flow<List<LoteProduccion>> =
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

    /** Ver [RegistroAcopioLocalDataSource.contarPendientes]. Fase 6 §6. */
    fun contarPendientes(usuarioId: String): Long = queries.contarPendientes(usuarioId).executeAsOne()

    /**
     * Ver [RegistroAcopioLocalDataSource.eliminarSincronizadosDeUsuario]. A diferencia de las otras 3
     * tablas de escritura, acá hay que borrar primero las filas hijas en `lote_produccion_registro_local`
     * (sin `usuario_id` propio, no se puede filtrar directo) antes de borrar el lote, o quedan huérfanas.
     */
    fun eliminarSincronizadosDeUsuario(usuarioId: String) {
        queries.transaction {
            queries.obtenerUuidsSincronizadosDeUsuario(usuarioId).executeAsList()
                .forEach { uuidCliente -> registroQueries.eliminarPorLote(uuidCliente) }
            queries.eliminarSincronizadosDeUsuario(usuarioId)
        }
    }
}

private fun Lote_produccion_local.aDominio(): LoteProduccion = LoteProduccion(
    uuidCliente = uuid_cliente,
    serverId = server_id,
    usuarioId = usuario_id,
    fecha = fecha,
    tipoQuesoId = tipo_queso_id,
    litrosUsados = litros_usados,
    unidadesObtenidas = unidades_obtenidas,
    syncStatus = sync_status,
    syncAttempts = sync_attempts,
    syncError = sync_error,
    nextAttemptAt = next_attempt_at,
    creadoEn = creado_en,
    sincronizadoEn = sincronizado_en,
)

private fun Lote_produccion_registro_local.aDominio(): LoteProduccionRegistro = LoteProduccionRegistro(
    loteUuidCliente = lote_uuid_cliente,
    registroAcopioUuidCliente = registro_acopio_uuid_cliente,
    registroAcopioServerId = registro_acopio_server_id,
)
