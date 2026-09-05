package com.ecolacteos.acopio.data.local.datasource

import com.ecolacteos.acopio.data.local.Registro_acopio_cache
import com.ecolacteos.acopio.data.local.RegistroAcopioCacheQueries
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import kotlinx.datetime.LocalDateTime

/**
 * Local Data Source de `registro_acopio_cache` (`PROMPT_FASE_04.md §6`, C-04). **Nunca** un
 * `reemplazarTodo`: se puebla fila por fila con [upsert], nunca con un borrado masivo (trampa #4).
 */
class RegistroAcopioCacheLocalDataSource(private val queries: RegistroAcopioCacheQueries) {

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
