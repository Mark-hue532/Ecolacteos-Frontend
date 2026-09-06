package com.ecolacteos.acopio.data.local.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ecolacteos.acopio.core.DispatcherProvider
import com.ecolacteos.acopio.data.local.Registro_acopio_local
import com.ecolacteos.acopio.data.local.RegistroAcopioLocalQueries
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime

/**
 * Local Data Source de `registro_acopio_local` (`PROMPT_FASE_04.md §6`). Única clase de esta fase que ve
 * `RegistroAcopioLocalQueries`/`Registro_acopio_local` -- el mapper fila-generada↔dominio ([aDominio])
 * vive acá, no se filtra hacia afuera ninguna referencia a un tipo generado por SQLDelight. La clase de
 * fila generada preserva el `snake_case` de la columna SQL tal cual (`Registro_acopio_local.uuid_cliente`,
 * no `uuidCliente`) -- confirmado compilando, no asumido (ver checkpoint).
 */
class RegistroAcopioLocalDataSource(
    private val queries: RegistroAcopioLocalQueries,
    private val dispatchers: DispatcherProvider,
) {
    fun insertar(registro: RegistroAcopio) {
        queries.insertar(
            uuidCliente = registro.uuidCliente,
            serverId = registro.serverId,
            usuarioId = registro.usuarioId,
            proveedorId = registro.proveedorId,
            unidadId = registro.unidadId,
            fechaHora = registro.fechaHora,
            litros = registro.litros,
            gpsLat = registro.gpsLat,
            gpsLng = registro.gpsLng,
            motivoObservacionId = registro.motivoObservacionId,
            litrosPorVoz = registro.litrosPorVoz,
            syncStatus = registro.syncStatus,
            syncAttempts = registro.syncAttempts,
            syncError = registro.syncError,
            nextAttemptAt = registro.nextAttemptAt,
            creadoEn = registro.creadoEn,
            sincronizadoEn = registro.sincronizadoEn,
        )
    }

    fun obtenerPorUuidCliente(uuidCliente: String): RegistroAcopio? =
        queries.obtenerPorUuidCliente(uuidCliente).executeAsOneOrNull()?.aDominio()

    fun obtenerPendientes(usuarioId: String, ahora: LocalDateTime): List<RegistroAcopio> =
        queries.obtenerPendientes(usuarioId, ahora).executeAsList().map { it.aDominio() }

    /** `Flow` nativo de SQLDelight (`app.cash.sqldelight.coroutines`) -- nunca envuelto a mano en un `Flow`. */
    fun observarTodos(usuarioId: String): Flow<List<RegistroAcopio>> =
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

    /**
     * Confirma la fila como `SYNCED` (`§6.1`). [serverId] es **nullable** a propósito: el lote
     * `POST /api/sync/{recurso}` confirma por `uuidCliente` y no devuelve el id de Postgres (`DATA-014`), así que
     * una fila puede quedar legítimamente `SYNCED` sin `server_id`. [sincronizadoEn] es, por el mismo
     * motivo, la hora del **dispositivo** al recibir la confirmación, no la del servidor.
     */
    fun marcarSincronizado(uuidCliente: String, serverId: String?, sincronizadoEn: LocalDateTime) {
        queries.marcarSincronizado(serverId = serverId, sincronizadoEn = sincronizadoEn, uuidCliente = uuidCliente)
    }

    /** Retención (`CLAUDE.md §3.6`): solo filas `SYNCED`, nunca toca `PENDING`/`SYNCING`/`FAILED`. Sin llamador todavía (Fase 9). */
    fun eliminarSincronizadosAntesDe(fecha: LocalDateTime) {
        queries.eliminarSincronizadosAntesDe(fecha)
    }
}

private fun Registro_acopio_local.aDominio(): RegistroAcopio = RegistroAcopio(
    uuidCliente = uuid_cliente,
    serverId = server_id,
    usuarioId = usuario_id,
    proveedorId = proveedor_id,
    unidadId = unidad_id,
    fechaHora = fecha_hora,
    litros = litros,
    gpsLat = gps_lat,
    gpsLng = gps_lng,
    motivoObservacionId = motivo_observacion_id,
    litrosPorVoz = litros_por_voz,
    syncStatus = sync_status,
    syncAttempts = sync_attempts,
    syncError = sync_error,
    nextAttemptAt = next_attempt_at,
    creadoEn = creado_en,
    sincronizadoEn = sincronizado_en,
)
