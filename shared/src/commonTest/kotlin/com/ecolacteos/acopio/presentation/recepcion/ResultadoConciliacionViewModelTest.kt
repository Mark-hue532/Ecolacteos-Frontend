package com.ecolacteos.acopio.presentation.recepcion

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleRecepcionUseCase
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests de `R-02` (`PROMPT_FASE_08E.md §8`, puntos 6-7). ONLINE-ONLY, sin cache (`§11.3`). */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultadoConciliacionViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    private fun crearViewModel(fixture: FixtureRepositorios, id: String = "rec-1") = viewModels.registrar(
        ResultadoConciliacionViewModel(id = id, obtenerDetalleRecepcionUseCase = ObtenerDetalleRecepcionUseCase(fixture.recepcionPlantaRepository)),
    )

    // 6: litrosRegistradosAcopio nulo se muestra como texto de "sin registros", nunca "0 L" (trampa #4).
    @Test
    fun `litrosRegistradosAcopio nulo muestra el texto de sin registros -- nunca 0 L`() = runTest {
        val fixture = FixtureRepositorios {
            if (it.url.encodedPath == Endpoints.recepcionPlantaPorId("rec-1")) {
                responderJson(
                    """{"id":"rec-1","fecha":"2026-09-06","turno":"UNICO","unidadId":"unidad-1",
                       |"litrosCampo":100.00,"litrosPlanta":98.00,"diferenciaPct":2.00,"estado":"OK"}
                    """.trimMargin(),
                )
            } else {
                error("no deberia pedirse otra ruta")
            }
        }
        val viewModel = crearViewModel(fixture)

        val estado = viewModel.uiState.esperarCargaCompleta()
        assertNull(estado.litrosRegistradosAcopioTexto, "SUM() sobre cero filas es NULL, no 0 -- la UI arma el texto explicativo, el UiState solo expone null")
    }

    // 7: un `estado` no reconocido se expone tal cual (UNKNOWN de reserva) sin romper la pantalla.
    @Test
    fun `estado desconocido se expone como UNKNOWN sin romper la pantalla`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson(
                """{"id":"rec-1","fecha":"2026-09-06","turno":"UNICO","unidadId":"unidad-1",
                   |"litrosCampo":100.00,"litrosPlanta":98.00,"diferenciaPct":2.00,"estado":"COSA_NUEVA"}
                """.trimMargin(),
            )
        }
        val viewModel = crearViewModel(fixture)

        val estado = viewModel.uiState.esperarCargaCompleta()
        assertEquals(true, estado.encontrado)
        assertEquals("UNKNOWN", estado.estadoTexto)
    }
}

/** Ver la nota equivalente en `DetalleRegistroAcopioViewModelTest.kt` -- misma razon, mismo patron. */
private suspend fun StateFlow<ResultadoConciliacionUiState>.esperarCargaCompleta(): ResultadoConciliacionUiState {
    var estado = ResultadoConciliacionUiState()
    test {
        estado = awaitItem()
        while (estado.cargando) estado = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return estado
}
