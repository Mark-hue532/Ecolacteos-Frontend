package com.ecolacteos.acopio.data.local.datasource

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.local.DispatcherProviderDeTest
import com.ecolacteos.acopio.data.local.crearAcopioDatabase
import com.ecolacteos.acopio.data.local.crearDriverDeTest
import com.ecolacteos.acopio.domain.model.AnalisisCalidad
import com.ecolacteos.acopio.domain.model.SyncStatus
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** CRUD + `CHECK` C-02 de `analisis_calidad_local` (`PROMPT_FASE_04.md §7`). */
class AnalisisCalidadLocalDataSourceTest {

    private fun analisis(
        uuidCliente: String = "an-1",
        registroAcopioUuidCliente: String? = "reg-propio",
        registroAcopioServerId: String? = null,
    ) = AnalisisCalidad(
        uuidCliente = uuidCliente,
        serverId = null,
        usuarioId = "usuario-1",
        registroAcopioUuidCliente = registroAcopioUuidCliente,
        registroAcopioServerId = registroAcopioServerId,
        folioMuestra = "F-001",
        agua = Decimal.parseString("3.20"),
        proteina = Decimal.parseString("3.10"),
        lactosa = null,
        densidad = null,
        temperatura = Decimal.parseString("4.50"),
        ph = null,
        aguaAnadida = false,
        syncStatus = SyncStatus.PENDING,
        syncAttempts = 0,
        syncError = null,
        nextAttemptAt = null,
        creadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
        sincronizadoEn = null,
    )

    private fun dataSource(driver: app.cash.sqldelight.db.SqlDriver = crearDriverDeTest()) =
        AnalisisCalidadLocalDataSource(crearAcopioDatabase(driver).analisisCalidadLocalQueries, DispatcherProviderDeTest)

    @Test
    fun `insertar con padre propio hace roundtrip exacto`() {
        val ds = dataSource()
        val original = analisis(registroAcopioUuidCliente = "reg-propio", registroAcopioServerId = null)

        ds.insertar(original)

        assertEquals(original, ds.obtenerPorUuidCliente("an-1"))
    }

    @Test
    fun `insertar con padre ajeno -- solo registroAcopioServerId -- tambien hace roundtrip`() {
        val ds = dataSource()
        val original = analisis(uuidCliente = "an-2", registroAcopioUuidCliente = null, registroAcopioServerId = "server-999")

        ds.insertar(original)

        assertEquals(original, ds.obtenerPorUuidCliente("an-2"))
    }

    /**
     * ⚠️ Corregido tras revisión: `AnalisisCalidad` (domain/model) ya tiene su propio `init {}` que
     * replica esta regla -- pasar por `ds.insertar(analisis(...))` nunca llega a SQLite, porque el
     * `require()` de Kotlin lanza al *construir* el objeto, antes de que `insertar` se ejecute. Estos dos
     * tests llaman directo a `AnalisisCalidadLocalQueries.insertar(...)` (el generado por SQLDelight,
     * bypaseando el modelo de dominio) para probar el `CHECK` real de SQLite, no el `require()` de Kotlin.
     */
    @Test
    fun `CHECK C-02 en SQLite -- ambas referencias nulas debe fallar`() {
        val queries = crearAcopioDatabase(crearDriverDeTest()).analisisCalidadLocalQueries

        assertFails {
            queries.insertar(
                uuidCliente = "an-invalido-ambas-null",
                serverId = null,
                usuarioId = "usuario-1",
                registroAcopioUuidCliente = null,
                registroAcopioServerId = null,
                folioMuestra = "F-invalido",
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
                creadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
                sincronizadoEn = null,
            )
        }
    }

    @Test
    fun `CHECK C-02 en SQLite -- ambas referencias no-nulas tambien debe fallar`() {
        val queries = crearAcopioDatabase(crearDriverDeTest()).analisisCalidadLocalQueries

        assertFails {
            queries.insertar(
                uuidCliente = "an-invalido-ambas-no-null",
                serverId = null,
                usuarioId = "usuario-1",
                registroAcopioUuidCliente = "reg-propio",
                registroAcopioServerId = "server-ajeno",
                folioMuestra = "F-invalido-2",
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
                creadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
                sincronizadoEn = null,
            )
        }
    }

    @Test
    fun `modelo de dominio tambien rechaza ambas referencias nulas antes de tocar SQLite`() {
        assertFails {
            AnalisisCalidad(
                uuidCliente = "an-x",
                serverId = null,
                usuarioId = "usuario-1",
                registroAcopioUuidCliente = null,
                registroAcopioServerId = null,
                folioMuestra = "F-002",
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
                creadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
                sincronizadoEn = null,
            )
        }
    }

    @Test
    fun `obtenerPendientes incluye PENDING_DEPENDENCY -- unico enum que la usa junto con LoteProduccion`() {
        val ds = dataSource()
        ds.insertar(analisis(uuidCliente = "dependiente"))
        ds.actualizarEstadoSync("dependiente", SyncStatus.PENDING_DEPENDENCY, 0, null, null)

        val pendientes = ds.obtenerPendientes("usuario-1", LocalDateTime(2026, 9, 5, 0, 0, 0))

        assertEquals(listOf("dependiente"), pendientes.map { it.uuidCliente })
    }
}
