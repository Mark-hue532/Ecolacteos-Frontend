package com.ecolacteos.acopio.data.local.datasource

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.local.AcopioDatabase
import com.ecolacteos.acopio.data.local.DispatcherProviderDeTest
import com.ecolacteos.acopio.data.local.crearAcopioDatabase
import com.ecolacteos.acopio.data.local.crearDriverDeTest
import com.ecolacteos.acopio.data.remote.dto.CicloCapital
import com.ecolacteos.acopio.domain.model.Comunicado
import com.ecolacteos.acopio.domain.model.MotivoObservacion
import com.ecolacteos.acopio.domain.model.PrediccionProveedor
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.model.TipoQueso
import com.ecolacteos.acopio.domain.model.Unidad
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

/** Las 7 tablas de catálogo "simples" (`PROMPT_FASE_04.md §7`): `reemplazarTodo` transaccional + lectura. */
class CatalogosLocalDataSourceTest {

    private val actualizadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0)

    private fun dataSource(database: AcopioDatabase = crearAcopioDatabase(crearDriverDeTest())) = CatalogosLocalDataSource(
        database.proveedorCacheQueries,
        database.unidadCacheQueries,
        database.motivoObservacionCacheQueries,
        database.tipoQuesoCacheQueries,
        database.comunicadoCacheQueries,
        database.comunicadoZonaCacheQueries,
        database.prediccionProveedorCacheQueries,
        database.precioLitroVigenteCacheQueries,
        DispatcherProviderDeTest,
    )

    @Test
    fun `reemplazarProveedores reemplaza completo -- la segunda llamada no arrastra la primera`() {
        val ds = dataSource()
        ds.reemplazarProveedores(listOf(Proveedor("p1", "Proveedor Uno", null, null, null, actualizadoEn)))
        ds.reemplazarProveedores(listOf(Proveedor("p2", "Proveedor Dos", "z1", "Zona 1", "QR-2", actualizadoEn)))

        assertEquals(listOf("p2"), flowActualComoLista(ds.observarProveedores()).map { it.id })
    }

    @Test
    fun `reemplazo transaccional -- una fila invalida deja la tabla en su estado anterior`() {
        val ds = dataSource()
        ds.reemplazarProveedores(listOf(Proveedor("p1", "Original", null, null, null, actualizadoEn)))

        // PRIMARY KEY duplicado a mitad de la segunda llamada -- debe fallar y no dejar la tabla ni vacía
        // ni a medias (PROMPT_FASE_04.md §7, "Reemplazo transaccional").
        assertFails {
            ds.reemplazarProveedores(
                listOf(
                    Proveedor("dup", "Nuevo A", null, null, null, actualizadoEn),
                    Proveedor("dup", "Nuevo B", null, null, null, actualizadoEn),
                ),
            )
        }

        assertEquals(listOf("p1"), flowActualComoLista(ds.observarProveedores()).map { it.id })
    }

    @Test
    fun `reemplazarUnidades roundtrip -- incluido capacidadTon decimal nullable`() {
        val ds = dataSource()
        val unidad = Unidad("u1", "ABC-123", Decimal.parseString("15.50"), "z1", "resp-1", "Juan Perez", actualizadoEn)

        ds.reemplazarUnidades(listOf(unidad))

        assertEquals(listOf(unidad), flowActualComoLista(ds.observarUnidades()))
    }

    @Test
    fun `reemplazarMotivosObservacion roundtrip`() {
        val ds = dataSource()
        val motivo = MotivoObservacion("m1", "Leche aguada", actualizadoEn)

        ds.reemplazarMotivosObservacion(listOf(motivo))

        assertEquals(listOf(motivo), flowActualComoLista(ds.observarMotivosObservacion()))
    }

    @Test
    fun `reemplazarTiposQueso roundtrip -- incluido CicloCapital y activo booleano`() {
        val ds = dataSource()
        val tipo = TipoQueso("t1", "Queso Andino", Decimal.parseString("12.50"), CicloCapital.RAPIDO, true, actualizadoEn)

        ds.reemplazarTiposQueso(listOf(tipo))

        assertEquals(listOf(tipo), flowActualComoLista(ds.observarTiposQueso()))
    }

    @Test
    fun `reemplazarComunicados escribe comunicado_cache y comunicado_zona_cache juntos`() {
        val ds = dataSource()
        val comunicado = Comunicado("c1", "Corte de ruta", LocalDateTime(2026, 9, 4, 8, 0, 0), listOf("Zona Norte", "Zona Sur"), actualizadoEn)

        ds.reemplazarComunicados(listOf(comunicado))

        val leido = flowActualComoLista(ds.observarComunicados()).single()
        assertEquals("c1", leido.id)
        assertEquals(setOf("Zona Norte", "Zona Sur"), leido.zonasNombres.toSet())
    }

    @Test
    fun `reemplazarPredicciones roundtrip`() {
        val ds = dataSource()
        val prediccion = PrediccionProveedor(
            "p1",
            LocalDate(2026, 9, 10),
            Decimal.parseString("100.00"),
            Decimal.parseString("150.00"),
            actualizadoEn,
        )

        ds.reemplazarPredicciones(listOf(prediccion))

        assertEquals(listOf(prediccion), flowActualComoLista(ds.observarPredicciones()))
    }

    @Test
    fun `precio vigente -- sin fila todavia devuelve null`() {
        val ds = dataSource()
        assertNull(ds.obtenerPrecioVigente())
    }

    @Test
    fun `precio vigente -- fila con precio NULL se colapsa al mismo null que -- sin fila-`() {
        val ds = dataSource()
        ds.reemplazarPrecioLitroVigente(precio = null, actualizadoEn = actualizadoEn)

        assertNull(ds.obtenerPrecioVigente())
    }

    @Test
    fun `precio vigente -- fila con precio configurado se lee completa`() {
        val ds = dataSource()
        ds.reemplazarPrecioLitroVigente(precio = Decimal.parseString("4.80"), actualizadoEn = actualizadoEn)

        val vigente = ds.obtenerPrecioVigente()
        assertEquals(Decimal.parseString("4.80"), vigente?.precio)
        assertEquals(actualizadoEn, vigente?.actualizadoEn)
    }

    /** Helper síncrono: en estos tests no necesitamos esperar cambios, solo leer el valor ya emitido. */
    private fun <T> flowActualComoLista(flow: kotlinx.coroutines.flow.Flow<List<T>>): List<T> =
        kotlinx.coroutines.runBlocking { flow.first() }
}
