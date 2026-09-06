package com.ecolacteos.acopio.data.local.datasource

import com.ecolacteos.acopio.data.local.Ruta_zona_cache
import com.ecolacteos.acopio.data.local.RutaZonaCacheQueries
import com.ecolacteos.acopio.domain.model.RutaProveedorOrden

/**
 * Local Data Source de `ruta_zona_cache` (`PROMPT_FASE_04.md §6`, C-04). Reemplazo acotado a una zona,
 * nunca un `DELETE` de la tabla entera -- descarga bajo demanda (`GET /zonas/{zonaId}/ruta`), no viaja en
 * `/sync/cambios`.
 */
class RutaZonaLocalDataSource(private val queries: RutaZonaCacheQueries) {

    fun reemplazarPorZona(zonaId: String, filas: List<RutaProveedorOrden>) {
        queries.transaction {
            queries.eliminarPorZona(zonaId)
            filas.forEach { fila ->
                queries.insert(
                    zonaId = fila.zonaId,
                    proveedorId = fila.proveedorId,
                    proveedorNombre = fila.proveedorNombre,
                    orden = fila.orden,
                    horaEstimada = fila.horaEstimada,
                    actualizadoEn = fila.actualizadoEn,
                )
            }
        }
    }

    fun obtenerPorZona(zonaId: String): List<RutaProveedorOrden> =
        queries.obtenerPorZona(zonaId).executeAsList().map { it.aDominio() }

    /** Logout (Fase 6 §6, C-09 RNF-12): borrado completo de todas las zonas, no acotado como el reemplazo normal. */
    fun borrarTodo() {
        queries.deleteAll()
    }
}

private fun Ruta_zona_cache.aDominio(): RutaProveedorOrden = RutaProveedorOrden(
    zonaId = zona_id,
    proveedorId = proveedor_id,
    proveedorNombre = proveedor_nombre,
    orden = orden,
    horaEstimada = hora_estimada,
    actualizadoEn = actualizado_en,
)
