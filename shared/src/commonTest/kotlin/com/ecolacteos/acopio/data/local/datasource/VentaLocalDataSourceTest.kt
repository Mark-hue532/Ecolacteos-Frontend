package com.ecolacteos.acopio.data.local.datasource

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.local.DispatcherProviderDeTest
import com.ecolacteos.acopio.data.local.crearAcopioDatabase
import com.ecolacteos.acopio.data.local.crearDriverDeTest
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.Venta
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** CRUD de `venta_local` (`PROMPT_FASE_04.md §7`). Sin dependencias externas -- nunca `PENDING_DEPENDENCY`. */
class VentaLocalDataSourceTest {

    private fun venta(uuidCliente: String = "venta-1", tipoCliente: TipoClienteVenta = TipoClienteVenta.MAYORISTA) = Venta(
        uuidCliente = uuidCliente,
        serverId = null,
        usuarioId = "usuario-1",
        fecha = LocalDate(2026, 9, 4),
        tipoCliente = tipoCliente,
        tipoQuesoId = "queso-1",
        cantidad = 10,
        precioUnitario = Decimal.parseString("25.50"),
        syncStatus = SyncStatus.PENDING,
        syncAttempts = 0,
        syncError = null,
        nextAttemptAt = null,
        creadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
        sincronizadoEn = null,
    )

    private fun dataSource() =
        VentaLocalDataSource(crearAcopioDatabase(crearDriverDeTest()).ventaLocalQueries, DispatcherProviderDeTest)

    @Test
    fun `insertar hace roundtrip exacto -- incluido el enum TipoClienteVenta`() {
        val ds = dataSource()
        val original = venta(tipoCliente = TipoClienteVenta.PROVEEDOR)

        ds.insertar(original)

        assertEquals(original, ds.obtenerPorUuidCliente("venta-1"))
    }

    @Test
    fun `obtenerPendientes no incluye PENDING_DEPENDENCY -- Venta nunca lo usa`() {
        val ds = dataSource()
        ds.insertar(venta())

        val pendientes = ds.obtenerPendientes("usuario-1", LocalDateTime(2026, 9, 5, 0, 0, 0))

        assertEquals(1, pendientes.size)
        assertEquals(SyncStatus.PENDING, pendientes.first().syncStatus)
    }

    @Test
    fun `actualizarEstadoSync y marcarSincronizado funcionan igual que en las otras 3 tablas`() {
        val ds = dataSource()
        ds.insertar(venta())

        ds.marcarSincronizado("venta-1", "server-1", LocalDateTime(2026, 9, 5, 8, 0, 0))

        val actualizado = ds.obtenerPorUuidCliente("venta-1")!!
        assertEquals(SyncStatus.SYNCED, actualizado.syncStatus)
        assertEquals("server-1", actualizado.serverId)
    }
}
