package com.ecolacteos.acopio.data.local.datasource

import app.cash.sqldelight.db.SqlDriver
import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.local.DispatcherProviderDeTest
import com.ecolacteos.acopio.data.local.crearAcopioDatabase
import com.ecolacteos.acopio.data.local.crearDriverDeTest
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.SyncStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** CRUD + retención + roundtrip de `registro_acopio_local` (`PROMPT_FASE_04.md §7`). */
class RegistroAcopioLocalDataSourceTest {

    private fun registro(
        uuidCliente: String = "reg-1",
        usuarioId: String = "usuario-1",
        fechaHora: LocalDateTime = LocalDateTime(2026, 9, 4, 6, 0, 0),
        syncStatus: SyncStatus = SyncStatus.PENDING,
        sincronizadoEn: LocalDateTime? = null,
    ) = RegistroAcopio(
        uuidCliente = uuidCliente,
        serverId = null,
        usuarioId = usuarioId,
        proveedorId = "prov-1",
        unidadId = "unidad-1",
        fechaHora = fechaHora,
        litros = Decimal.parseString("120.50"),
        gpsLat = Decimal.parseString("-12.045678"),
        gpsLng = Decimal.parseString("-77.032817"),
        motivoObservacionId = null,
        litrosPorVoz = false,
        syncStatus = syncStatus,
        syncAttempts = 0,
        syncError = null,
        nextAttemptAt = null,
        creadoEn = LocalDateTime(2026, 9, 4, 6, 0, 1),
        sincronizadoEn = sincronizadoEn,
    )

    @Test
    fun `insertar y obtenerPorUuidCliente hace roundtrip exacto -- incluidos decimales y fecha`() {
        val dataSource = RegistroAcopioLocalDataSource(
            crearAcopioDatabase(crearDriverDeTest()).registroAcopioLocalQueries,
            DispatcherProviderDeTest,
        )
        val original = registro()

        dataSource.insertar(original)
        val leido = dataSource.obtenerPorUuidCliente("reg-1")

        assertEquals(original, leido)
    }

    /**
     * Dedicado a propósito (no solo incidental vía [registro], que ya usa GPS de Lima): confirma, a través
     * del roundtrip real de SQLite -- no solo del `ColumnAdapter` aislado --, que `gps_lat`/`gps_lng`
     * negativos (Perú entero: latitud y longitud negativas) preservan los 6 decimales exactos.
     */
    @Test
    fun `roundtrip de GPS con latitud y longitud negativas -- coordenadas de Peru`() {
        val dataSource = RegistroAcopioLocalDataSource(
            crearAcopioDatabase(crearDriverDeTest()).registroAcopioLocalQueries,
            DispatcherProviderDeTest,
        )
        // Plaza de Armas de Lima, ambos negativos (hemisferio sur / oeste).
        val original = registro(uuidCliente = "reg-peru").copy(
            gpsLat = Decimal.parseString("-12.045678"),
            gpsLng = Decimal.parseString("-77.030348"),
        )

        dataSource.insertar(original)
        val leido = dataSource.obtenerPorUuidCliente("reg-peru")

        assertEquals(Decimal.parseString("-12.045678"), leido?.gpsLat)
        assertEquals(Decimal.parseString("-77.030348"), leido?.gpsLng)
    }

    @Test
    fun `obtenerPorUuidCliente devuelve null si no existe`() {
        val dataSource = RegistroAcopioLocalDataSource(
            crearAcopioDatabase(crearDriverDeTest()).registroAcopioLocalQueries,
            DispatcherProviderDeTest,
        )
        assertNull(dataSource.obtenerPorUuidCliente("no-existe"))
    }

    @Test
    fun `obtenerPendientes solo trae PENDING o FAILED con next_attempt_at vencido`() {
        val dataSource = RegistroAcopioLocalDataSource(
            crearAcopioDatabase(crearDriverDeTest()).registroAcopioLocalQueries,
            DispatcherProviderDeTest,
        )
        dataSource.insertar(registro(uuidCliente = "pendiente", syncStatus = SyncStatus.PENDING))
        dataSource.insertar(registro(uuidCliente = "sincronizado", syncStatus = SyncStatus.SYNCED))

        val pendientes = dataSource.obtenerPendientes("usuario-1", LocalDateTime(2026, 9, 5, 0, 0, 0))

        assertEquals(listOf("pendiente"), pendientes.map { it.uuidCliente })
    }

    @Test
    fun `observarTodos emite por usuario_id via Flow nativo`() = runTest {
        val dataSource = RegistroAcopioLocalDataSource(
            crearAcopioDatabase(crearDriverDeTest()).registroAcopioLocalQueries,
            DispatcherProviderDeTest,
        )

        dataSource.observarTodos("usuario-1").test {
            assertEquals(emptyList(), awaitItem())

            dataSource.insertar(registro())
            assertEquals(listOf("reg-1"), awaitItem().map { it.uuidCliente })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `actualizarEstadoSync cambia status -- intentos y error`() {
        val dataSource = RegistroAcopioLocalDataSource(
            crearAcopioDatabase(crearDriverDeTest()).registroAcopioLocalQueries,
            DispatcherProviderDeTest,
        )
        dataSource.insertar(registro())

        dataSource.actualizarEstadoSync(
            uuidCliente = "reg-1",
            status = SyncStatus.FAILED,
            syncAttempts = 2,
            syncError = "timeout",
            nextAttemptAt = LocalDateTime(2026, 9, 5, 0, 0, 0),
        )

        val actualizado = dataSource.obtenerPorUuidCliente("reg-1")!!
        assertEquals(SyncStatus.FAILED, actualizado.syncStatus)
        assertEquals(2, actualizado.syncAttempts)
        assertEquals("timeout", actualizado.syncError)
    }

    @Test
    fun `marcarSincronizado confirma sync -- SYNCED -- resetea intentos y error`() {
        val dataSource = RegistroAcopioLocalDataSource(
            crearAcopioDatabase(crearDriverDeTest()).registroAcopioLocalQueries,
            DispatcherProviderDeTest,
        )
        dataSource.insertar(registro(syncStatus = SyncStatus.SYNCING))
        dataSource.actualizarEstadoSync("reg-1", SyncStatus.FAILED, 3, "error previo", null)

        val sincronizadoEn = LocalDateTime(2026, 9, 5, 8, 0, 0)
        dataSource.marcarSincronizado("reg-1", "server-123", sincronizadoEn)

        val actualizado = dataSource.obtenerPorUuidCliente("reg-1")!!
        assertEquals(SyncStatus.SYNCED, actualizado.syncStatus)
        assertEquals("server-123", actualizado.serverId)
        assertEquals(sincronizadoEn, actualizado.sincronizadoEn)
        assertEquals(0, actualizado.syncAttempts)
        assertNull(actualizado.syncError)
    }

    @Test
    fun `retencion -- eliminarSincronizadosAntesDe no toca PENDING ni SYNCING ni FAILED`() {
        val dataSource = RegistroAcopioLocalDataSource(
            crearAcopioDatabase(crearDriverDeTest()).registroAcopioLocalQueries,
            DispatcherProviderDeTest,
        )
        val fechaVieja = LocalDateTime(2026, 1, 1, 0, 0, 0)
        dataSource.insertar(registro(uuidCliente = "pendiente", syncStatus = SyncStatus.PENDING))
        dataSource.insertar(registro(uuidCliente = "syncing", syncStatus = SyncStatus.SYNCING))
        dataSource.insertar(registro(uuidCliente = "failed", syncStatus = SyncStatus.FAILED))
        dataSource.insertar(
            registro(uuidCliente = "sincronizado-viejo", syncStatus = SyncStatus.SYNCED, sincronizadoEn = fechaVieja),
        )

        dataSource.eliminarSincronizadosAntesDe(LocalDateTime(2026, 9, 5, 0, 0, 0))

        assertEquals(setOf("pendiente", "syncing", "failed"), listOf("pendiente", "syncing", "failed", "sincronizado-viejo")
            .filter { dataSource.obtenerPorUuidCliente(it) != null }.toSet())
    }

    @Test
    fun `restart simulado -- recrear el LocalDataSource sobre el mismo SqlDriver conserva los datos`() {
        val driver: SqlDriver = crearDriverDeTest()
        val dataSource1 = RegistroAcopioLocalDataSource(
            crearAcopioDatabase(driver).registroAcopioLocalQueries,
            DispatcherProviderDeTest,
        )
        dataSource1.insertar(registro(syncStatus = SyncStatus.PENDING))

        // "Cerrar y reabrir la app": se destruye y recrea la capa de arriba, pero el mismo SqlDriver sigue
        // vivo (§17.1) -- un driver JDBC en memoria SÍ perdería los datos si se recreara el driver también
        // (trampa #10 de PROMPT_FASE_04.md).
        val dataSource2 = RegistroAcopioLocalDataSource(
            crearAcopioDatabase(driver).registroAcopioLocalQueries,
            DispatcherProviderDeTest,
        )

        val registroReabierto = dataSource2.obtenerPorUuidCliente("reg-1")
        assertEquals(SyncStatus.PENDING, registroReabierto?.syncStatus)
    }
}
