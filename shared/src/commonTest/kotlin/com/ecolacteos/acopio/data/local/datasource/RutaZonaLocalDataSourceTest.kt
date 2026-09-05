package com.ecolacteos.acopio.data.local.datasource

import com.ecolacteos.acopio.data.local.crearAcopioDatabase
import com.ecolacteos.acopio.data.local.crearDriverDeTest
import com.ecolacteos.acopio.domain.model.RutaProveedorOrden
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `ruta_zona_cache` (`PROMPT_FASE_04.md §7`, C-04): reemplazo acotado a una zona, nunca la tabla entera.
 */
class RutaZonaLocalDataSourceTest {

    private val actualizadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0)

    private fun fila(zonaId: String, proveedorId: String, orden: Int) = RutaProveedorOrden(
        zonaId = zonaId,
        proveedorId = proveedorId,
        proveedorNombre = "Proveedor $proveedorId",
        orden = orden,
        horaEstimada = LocalTime(6, 0, 0),
        actualizadoEn = actualizadoEn,
    )

    @Test
    fun `reemplazarPorZona no toca las filas de otras zonas`() {
        val ds = RutaZonaLocalDataSource(crearAcopioDatabase(crearDriverDeTest()).rutaZonaCacheQueries)
        ds.reemplazarPorZona("zona-1", listOf(fila("zona-1", "p1", 1)))
        ds.reemplazarPorZona("zona-2", listOf(fila("zona-2", "p2", 1)))

        ds.reemplazarPorZona("zona-1", listOf(fila("zona-1", "p3", 1), fila("zona-1", "p4", 2)))

        assertEquals(listOf("p3", "p4"), ds.obtenerPorZona("zona-1").map { it.proveedorId })
        assertEquals(listOf("p2"), ds.obtenerPorZona("zona-2").map { it.proveedorId })
    }

    @Test
    fun `obtenerPorZona ordena por orden ascendente`() {
        val ds = RutaZonaLocalDataSource(crearAcopioDatabase(crearDriverDeTest()).rutaZonaCacheQueries)
        ds.reemplazarPorZona("zona-1", listOf(fila("zona-1", "segundo", 2), fila("zona-1", "primero", 1)))

        assertEquals(listOf("primero", "segundo"), ds.obtenerPorZona("zona-1").map { it.proveedorId })
    }

    @Test
    fun `horaEstimada roundtrip con segundos explicitos`() {
        val ds = RutaZonaLocalDataSource(crearAcopioDatabase(crearDriverDeTest()).rutaZonaCacheQueries)
        ds.reemplazarPorZona("zona-1", listOf(fila("zona-1", "p1", 1)))

        assertEquals(LocalTime(6, 0, 0), ds.obtenerPorZona("zona-1").single().horaEstimada)
    }
}
