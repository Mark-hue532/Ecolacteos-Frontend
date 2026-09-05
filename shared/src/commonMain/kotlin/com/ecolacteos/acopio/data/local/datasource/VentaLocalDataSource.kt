package com.ecolacteos.acopio.data.local.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ecolacteos.acopio.core.DispatcherProvider
import com.ecolacteos.acopio.data.local.Venta_local
import com.ecolacteos.acopio.data.local.VentaLocalQueries
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.Venta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime

/** Local Data Source de `venta_local` (`PROMPT_FASE_04.md §6`). Sin dependencias externas. */
class VentaLocalDataSource(
    private val queries: VentaLocalQueries,
    private val dispatchers: DispatcherProvider,
) {
    fun insertar(venta: Venta) {
        queries.insertar(
            uuidCliente = venta.uuidCliente,
            serverId = venta.serverId,
            usuarioId = venta.usuarioId,
            fecha = venta.fecha,
            tipoCliente = venta.tipoCliente,
            tipoQuesoId = venta.tipoQuesoId,
            cantidad = venta.cantidad,
            precioUnitario = venta.precioUnitario,
            syncStatus = venta.syncStatus,
            syncAttempts = venta.syncAttempts,
            syncError = venta.syncError,
            nextAttemptAt = venta.nextAttemptAt,
            creadoEn = venta.creadoEn,
            sincronizadoEn = venta.sincronizadoEn,
        )
    }

    fun obtenerPorUuidCliente(uuidCliente: String): Venta? =
        queries.obtenerPorUuidCliente(uuidCliente).executeAsOneOrNull()?.aDominio()

    fun obtenerPendientes(usuarioId: String, ahora: LocalDateTime): List<Venta> =
        queries.obtenerPendientes(usuarioId, ahora).executeAsList().map { it.aDominio() }

    fun observarTodos(usuarioId: String): Flow<List<Venta>> =
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

    fun actualizarServerId(uuidCliente: String, serverId: String, sincronizadoEn: LocalDateTime) {
        queries.actualizarServerId(serverId = serverId, sincronizadoEn = sincronizadoEn, uuidCliente = uuidCliente)
    }

    fun eliminarSincronizadosAntesDe(fecha: LocalDateTime) {
        queries.eliminarSincronizadosAntesDe(fecha)
    }
}

private fun Venta_local.aDominio(): Venta = Venta(
    uuidCliente = uuid_cliente,
    serverId = server_id,
    usuarioId = usuario_id,
    fecha = fecha,
    tipoCliente = tipo_cliente,
    tipoQuesoId = tipo_queso_id,
    cantidad = cantidad,
    precioUnitario = precio_unitario,
    syncStatus = sync_status,
    syncAttempts = sync_attempts,
    syncError = sync_error,
    nextAttemptAt = next_attempt_at,
    creadoEn = creado_en,
    sincronizadoEn = sincronizado_en,
)
