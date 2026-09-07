package com.ecolacteos.acopio.presentation.calidad

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.usecase.AnexarCorreccionUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleRegistroAcopioUseCase
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.ConnectivityObserverFake
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val REGISTRO_JSON = """{"id":"srv-1","uuidCliente":"uuid-x","proveedorId":"prov-1","proveedorNombre":"Granja",
    "unidadId":"unidad-1","fechaHora":"2026-09-06T08:00:00","litros":120.50,
    "litrosPorVoz":false,"sincronizadoEn":"2026-09-06T21:00:00"}"""

/** Tests de `C-06` (`PROMPT_FASE_08E.md §8`, puntos 10-11). ONLINE-ONLY, sin cola (`DATA-004`, trampa #1). */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrarCorreccionViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    private fun crearViewModel(fixture: FixtureRepositorios, registroAcopioId: String = "srv-1") = viewModels.registrar(
        RegistrarCorreccionViewModel(
            registroAcopioId = registroAcopioId,
            anexarCorreccionUseCase = AnexarCorreccionUseCase(fixture.correccionRegistroRepository),
            obtenerDetalleRegistroAcopioUseCase = ObtenerDetalleRegistroAcopioUseCase(fixture.registroAcopioRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
        ),
    )

    // 10: sin conexion no se dispara ninguna request de correccion (el GET de "litros actual" es aparte -- A-06 ya cachea).
    @Test
    fun `sin conexion no dispara ninguna request de correccion`() = runTest {
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = false)) { request ->
            if (request.url.encodedPath == Endpoints.registroAcopioPorId("srv-1")) {
                responderJson(REGISTRO_JSON)
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val viewModel = crearViewModel(fixture)
        viewModel.uiState.esperarLitrosActualCargado()

        viewModel.onEvent(RegistrarCorreccionEvent.LitrosCorregidoCambio("100.00"))
        viewModel.onEvent(RegistrarCorreccionEvent.EnviarPresionado)
        // Abrir la confirmacion no requiere red -- solo enviar la confirma.
        viewModel.onEvent(RegistrarCorreccionEvent.ConfirmarPresionado)

        assertEquals(
            0,
            fixture.cuantasVecesSePidio(Endpoints.correccionesDeRegistro("srv-1")),
            "DATA-004/§18.7/trampa #1 -- sin conexion nunca se dispara ni se encola",
        )
    }

    // 11: sin el evento de confirmacion no se envia nada, y el dialogo expone el valor anterior y el nuevo.
    @Test
    fun `confirmacion obligatoria -- sin confirmar no envia nada y expone anterior y nuevo`() = runTest {
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = true)) { request ->
            when (request.url.encodedPath) {
                Endpoints.registroAcopioPorId("srv-1") -> responderJson(REGISTRO_JSON)
                Endpoints.correccionesDeRegistro("srv-1") -> responderJson(
                    """{"id":"corr-1","registroAcopioId":"srv-1","litrosAnterior":120.50,"litrosCorregido":100.00,
                       |"usuarioNombre":"Ana","creadoEn":"2026-09-06T09:00:00"}
                    """.trimMargin(),
                )
                else -> responderJson(cuerpoCambiosVacio())
            }
        }
        val viewModel = crearViewModel(fixture)
        val estadoCargado = viewModel.uiState.esperarLitrosActualCargado()
        assertEquals("120.50", estadoCargado.litrosActualTexto)

        viewModel.onEvent(RegistrarCorreccionEvent.LitrosCorregidoCambio("100.00"))
        viewModel.onEvent(RegistrarCorreccionEvent.EnviarPresionado)

        val estadoConfirmacion = viewModel.uiState.value
        assertTrue(estadoConfirmacion.mostrarConfirmacion, "confirmacion obligatoria antes de enviar (trampa #10)")
        assertEquals("120.50", estadoConfirmacion.litrosActualTexto, "el dialogo nombra el valor anterior")
        assertEquals("100.00", estadoConfirmacion.litrosCorregidoTexto, "y el valor nuevo")
        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.correccionesDeRegistro("srv-1")), "abrir la confirmacion todavia no envia nada")

        viewModel.effect.test {
            viewModel.onEvent(RegistrarCorreccionEvent.ConfirmarPresionado)
            assertEquals(RegistrarCorreccionEffect.GuardadoConExito, awaitItem())
        }
        assertEquals(1, fixture.cuantasVecesSePidio(Endpoints.correccionesDeRegistro("srv-1")), "recien al confirmar se envia")
    }
}

/** Ver la nota equivalente en `DetalleRegistroAcopioViewModelTest.kt` -- misma razon, mismo patron. */
private suspend fun StateFlow<RegistrarCorreccionUiState>.esperarLitrosActualCargado(): RegistrarCorreccionUiState {
    var estado = RegistrarCorreccionUiState()
    test {
        estado = awaitItem()
        while (estado.cargandoActual) estado = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return estado
}
