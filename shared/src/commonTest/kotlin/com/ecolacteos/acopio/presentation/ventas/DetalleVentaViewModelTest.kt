package com.ecolacteos.acopio.presentation.ventas

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.data.repository.NuevaVenta
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleVentaUseCase
import com.ecolacteos.acopio.synchronization.responderJson
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests de `V-03` (`PROMPT_FASE_07.md §9`, puntos 7 y 8: `total` real del servidor, nunca calculado). */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleVentaViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 8: sincronizada -- muestra el total tal cual lo devolvio el servidor, no cantidad x precioUnitario local.
    @Test
    fun `venta sincronizada muestra el total real del servidor`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson(
                """{"id":"server-1","fecha":"2026-09-06","tipoCliente":"MAYORISTA","tipoQuesoNombre":"Queso Andino",
                    |"cantidad":10,"precioUnitario":25.50,"total":260.00}
                """.trimMargin().replace("\n", ""),
            )
        }
        val uuidCliente = fixture.ventaRepository.crear(
            NuevaVenta(LocalDate(2026, 9, 6), TipoClienteVenta.MAYORISTA, "queso-1", 10, Decimal.parseString("25.50")),
        )
        fixture.ventasLocal.marcarSincronizado(uuidCliente, "server-1", fixture.reloj.now().toLocalDateTime(TimeZone.UTC))

        val viewModel = DetalleVentaViewModel(
            uuidCliente = uuidCliente,
            obtenerDetalleVentaUseCase = ObtenerDetalleVentaUseCase(fixture.ventaRepository),
            observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
        )

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()
            // El "10 x 25.50 = 255.00" del cálculo local difiere a propósito del "260.00" del servidor en
            // este test -- si `V-03` mostrara el cálculo local en vez del real, esta aserción fallaría.
            assertEquals("S/ 260.00", estado.totalTexto)
            assertEquals(EstadoSincronizacion.Sincronizado, estado.estadoSync)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // 7 (parcial) / 8: sin sincronizar -- total en null, nunca "0" ni un cálculo local.
    @Test
    fun `venta sin sincronizar no muestra total -- nunca un calculo local ni 0`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val uuidCliente = fixture.ventaRepository.crear(
            NuevaVenta(LocalDate(2026, 9, 6), TipoClienteVenta.PROVEEDOR, "queso-1", 4, Decimal.parseString("9.00")),
        )

        val viewModel = DetalleVentaViewModel(
            uuidCliente = uuidCliente,
            obtenerDetalleVentaUseCase = ObtenerDetalleVentaUseCase(fixture.ventaRepository),
            observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
        )

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()
            assertNull(estado.totalTexto)
            assertEquals(EstadoSincronizacion.Pendiente, estado.estadoSync)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
