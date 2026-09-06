package com.ecolacteos.acopio.data.local.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ecolacteos.acopio.core.DispatcherProvider
import com.ecolacteos.acopio.data.local.Registro_acopio_cache
import com.ecolacteos.acopio.data.local.RegistroAcopioCacheQueries
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime

/**
 * Local Data Source de `registro_acopio_cache` (`PROMPT_FASE_04.md §6`, C-04). **Nunca** un
 * `reemplazarTodo`: se puebla fila por fila con [upsert], nunca con un borrado masivo (trampa #4).
 *
 * `dispatchers` se agrega en Fase 6 (antes esta clase no tenía ningún método reactivo) -- para
 * [observarPorProveedor], que `RegistroAcopioRepository.observarHistorialProveedor` combina con el lado
 * propio de `registro_acopio_local`.
 */
class RegistroAcopioCacheLocalDataSource(
    private val queries: RegistroAcopioCacheQueries,
    private val dispatchers: DispatcherProvider,
) {

    fun upsert(fila: RegistroAcopioReferencia) {
        queries.upsert(
            id = fila.id,
            uuidCliente = fila.uuidCliente,
            proveedorId = fila.proveedorId,
            proveedorNombre = fila.proveedorNombre,
            fechaHora = fila.fechaHora,
            litros = fila.litros,
            tieneObservacion = fila.tieneObservacion,
            origen = fila.origen,
            actualizadoEn = fila.actualizadoEn,
        )
    }

    fun obtenerPorServerId(id: String): RegistroAcopioReferencia? =
        queries.obtenerPorServerId(id).executeAsOneOrNull()?.aDominio()

    /** Historial de un proveedor puntual, lado ajeno (`§16.4`, Fase 6). Ver la nota de deduplicación en el `.sq`. */
    fun obtenerPorProveedor(proveedorId: String): List<RegistroAcopioReferencia> =
        queries.obtenerPorProveedor(proveedorId).executeAsList().map { it.aDominio() }

    /** Igual que [obtenerPorProveedor], reactivo -- ver `RegistroAcopioRepository.observarHistorialProveedor`. */
    fun observarPorProveedor(proveedorId: String): Flow<List<RegistroAcopioReferencia>> =
        queries.obtenerPorProveedor(proveedorId).asFlow().mapToList(dispatchers.io)
            .map { filas -> filas.map { it.aDominio() } }

    /** Logout (Fase 6 §6, C-09 RNF-12): borrado completo -- la única excepción a "nunca en masa" de Fase 4. */
    fun borrarTodo() {
        queries.deleteAll()
    }

    /**
     * Retención C-04 (`MOBILE_ARCHITECTURE.md §11.4`): 30 días desde `actualizado_en`, salvo filas
     * referenciadas por un hijo local todavía no `SYNCED`. Sin llamador todavía (Fase 9).
     */
    fun eliminarAntesDeSalvoReferenciados(fecha: LocalDateTime) {
        queries.eliminarAntesDeSalvoReferenciados(fecha)
    }
}

private fun Registro_acopio_cache.aDominio(): RegistroAcopioReferencia = RegistroAcopioReferencia(
    id = id,
    uuidCliente = uuid_cliente,
    proveedorId = proveedor_id,
    proveedorNombre = proveedor_nombre,
    fechaHora = fecha_hora,
    litros = litros,
    tieneObservacion = tiene_observacion,
    origen = origen,
    actualizadoEn = actualizado_en,
)
