package com.ecolacteos.acopio.data.local.datasource

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.local.crearAcopioDatabase
import com.ecolacteos.acopio.data.local.crearDriverDeTest
import com.ecolacteos.acopio.domain.model.AnalisisCalidad
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import com.ecolacteos.acopio.domain.model.SyncStatus
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `registro_acopio_cache` (`PROMPT_FASE_04.md §7`, C-04): `upsert` fila por fila (nunca `reemplazarTodo`,
 * trampa #4) y la retención que respeta hijos locales todavía no `SYNCED`.
 */
class RegistroAcopioCacheLocalDataSourceTest {

    private fun fila(id: String = "server-1", origen: Origen = Origen.RESUMEN, actualizadoEn: LocalDateTime = LocalDateTime(2026, 9, 4, 6, 0, 0)) =
        RegistroAcopioReferencia(
            id = id,
            uuidCliente = null,
            proveedorId = null,
            proveedorNombre = null,
            fechaHora = LocalDateTime(2026, 9, 3, 10, 0, 0),
            litros = Decimal.parseString("300.00"),
            tieneObservacion = true,
            origen = origen,
            actualizadoEn = actualizadoEn,
        )

    @Test
    fun `upsert es idempotente -- una segunda llamada con el mismo id reemplaza la fila -- no duplica`() {
        val ds = RegistroAcopioCacheLocalDataSource(crearAcopioDatabase(crearDriverDeTest()).registroAcopioCacheQueries)
        ds.upsert(fila(origen = Origen.RESUMEN))
        ds.upsert(fila(origen = Origen.DETALLE))

        assertEquals(Origen.DETALLE, ds.obtenerPorServerId("server-1")?.origen)
    }

    @Test
    fun `obtenerPorServerId devuelve null si no existe`() {
        val ds = RegistroAcopioCacheLocalDataSource(crearAcopioDatabase(crearDriverDeTest()).registroAcopioCacheQueries)
        assertNull(ds.obtenerPorServerId("no-existe"))
    }

    @Test
    fun `retencion -- no borra una fila referenciada por un AnalisisCalidad todavia no SYNCED`() {
        val database = crearAcopioDatabase(crearDriverDeTest())
        val cacheDs = RegistroAcopioCacheLocalDataSource(database.registroAcopioCacheQueries)
        val analisisDs = AnalisisCalidadLocalDataSource(database.analisisCalidadLocalQueries, com.ecolacteos.acopio.data.local.DispatcherProviderDeTest)

        val fechaVieja = LocalDateTime(2026, 1, 1, 0, 0, 0)
        cacheDs.upsert(fila(id = "referenciado", actualizadoEn = fechaVieja))
        cacheDs.upsert(fila(id = "huerfano", actualizadoEn = fechaVieja))

        analisisDs.insertar(
            AnalisisCalidad(
                uuidCliente = "an-1",
                serverId = null,
                usuarioId = "usuario-1",
                registroAcopioUuidCliente = null,
                registroAcopioServerId = "referenciado",
                folioMuestra = "F-1",
                agua = null,
                proteina = null,
                lactosa = null,
                densidad = null,
                temperatura = null,
                ph = null,
                aguaAnadida = false,
                syncStatus = SyncStatus.PENDING,
                syncAttempts = 0,
                syncError = null,
                nextAttemptAt = null,
                creadoEn = fechaVieja,
                sincronizadoEn = null,
            ),
        )

        cacheDs.eliminarAntesDeSalvoReferenciados(LocalDateTime(2026, 9, 5, 0, 0, 0))

        assertEquals("referenciado", cacheDs.obtenerPorServerId("referenciado")?.id)
        assertNull(cacheDs.obtenerPorServerId("huerfano"))
    }
}
