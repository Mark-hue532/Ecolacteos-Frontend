package com.ecolacteos.acopio.presentation.ventas

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.data.repository.NuevaVenta
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.Venta
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarVentasDelDiaUseCase
import com.ecolacteos.acopio.synchronization.GestorSesionFake
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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `2026-09-06` -- misma fecha del `RelojFijo` por defecto de [FixtureRepositorios]
 * (`Instant.parse("2026-09-06T12:00:00Z")`), para que "hoy" del `ViewModel` coincida sin pasar un reloj
 * aparte.
 */
private val HOY = LocalDate(2026, 9, 6)
private val AYER = LocalDate(2026, 9, 5)

/** Tests de `V-01` (`PROMPT_FASE_07.md §9`, puntos 6 y 10). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeVentasViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(fixture: FixtureRepositorios): HomeVentasViewModel = HomeVentasViewModel(
        observarVentasDelDiaUseCase = ObservarVentasDelDiaUseCase(fixture.ventaRepository, fixture.reloj, TimeZone.UTC),
        observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
    )

    // 6: aparicion optimista -- crear una venta y verla llegar por el Flow sin volver a consultar a mano.
    @Test
    fun `una venta creada aparece de inmediato con badge pendiente y luego SYNCED al confirmar`() = runTest {
        val fixture = FixtureRepositorios { responderJson("""{"confirmados":["uuid-1"],"errores":[]}""") }
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            awaitItem() // estado con el que arranca el ViewModel (sin ventas todavia)

            fixture.ventaRepository.crear(
                NuevaVenta(
                    fecha = HOY,
                    tipoCliente = TipoClienteVenta.MAYORISTA,
                    tipoQuesoId = "queso-1",
                    cantidad = 5,
                    precioUnitario = Decimal.parseString("10.00"),
                ),
            )

            val conLaVenta = awaitItem()
            assertEquals(1, conLaVenta.ventas.size)
            assertEquals(EstadoSincronizacion.Pendiente, conLaVenta.ventas.first().estadoSync)

            val ahora = fixture.reloj.now().toLocalDateTime(TimeZone.UTC)
            fixture.ventasLocal.marcarSincronizado(conLaVenta.ventas.first().uuidCliente, "server-1", ahora)

            val sincronizada = awaitItem()
            assertEquals(EstadoSincronizacion.Sincronizado, sincronizada.ventas.first().estadoSync)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // 10: el orden/filtro de la lista usa solo `fecha` -- nunca un timestamp de servidor.
    @Test
    fun `solo se listan las ventas de hoy segun el campo fecha del dispositivo`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.ventasLocal.insertar(ventaDePrueba("de-hoy", HOY))
        fixture.ventasLocal.insertar(ventaDePrueba("de-ayer", AYER))

        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()
            assertEquals(listOf("de-hoy"), estado.ventas.map { it.uuidCliente })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun ventaDePrueba(uuidCliente: String, fecha: LocalDate) = Venta(
        uuidCliente = uuidCliente,
        serverId = null,
        usuarioId = GestorSesionFake.USUARIO_ID,
        fecha = fecha,
        tipoCliente = TipoClienteVenta.MAYORISTA,
        tipoQuesoId = "queso-1",
        cantidad = 3,
        precioUnitario = Decimal.parseString("15.00"),
        syncStatus = SyncStatus.PENDING,
        syncAttempts = 0,
        syncError = null,
        nextAttemptAt = null,
        creadoEn = LocalDateTime(2026, 9, fecha.day, 10, 0, 0),
        sincronizadoEn = null,
    )
}
