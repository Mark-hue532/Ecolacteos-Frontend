package com.ecolacteos.acopio.presentation.produccion

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleLoteUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test de `P-04` (`PROMPT_FASE_08D.md §8`, punto 14). */
class DetalleLoteViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    private fun crearViewModel(fixture: FixtureRepositorios, id: String = "srv-1") = viewModels.registrar(
        DetalleLoteViewModel(
            id = id,
            obtenerDetalleLoteUseCase = ObtenerDetalleLoteUseCase(fixture.loteProduccionRepository),
            observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
        ),
    )

    // 14a: rendimientoPct nulo -- se expone como "no calculado" y no hay comparacion en el UiState.
    @Test
    fun `rendimientoPct nulo se expone sin comparacion`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.lotePorId("srv-1")) {
                responderJson(
                    """{"id":"srv-1","fecha":"2026-09-06","tipoQuesoNombre":"Queso Andino","litrosUsados":0.00,
                        "unidadesObtenidas":0,"rendimientoEsperadoPct":12.00,"registroAcopioIds":["r1"]}""",
                )
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            assertEquals("Queso Andino", estado.tipoQuesoNombreTexto)
            assertEquals("12.00", estado.rendimientoEsperadoTexto)
            assertNull(estado.rendimientoRealTexto, "no calculado -- litrosUsados=0")
            assertFalse(estado.hayComparacion, "nunca se dibuja una comparacion contra un valor inexistente")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // 14b: con rendimientoPct presente, la comparacion aparece con 2 decimales.
    @Test
    fun `rendimientoPct presente muestra la comparacion con 2 decimales`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.lotePorId("srv-2")) {
                responderJson(
                    """{"id":"srv-2","fecha":"2026-09-06","tipoQuesoNombre":"Queso Andino","litrosUsados":100.00,
                        "unidadesObtenidas":45,"rendimientoPct":11.5,"rendimientoEsperadoPct":12.00,"registroAcopioIds":["r1"]}""",
                )
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val viewModel = crearViewModel(fixture, id = "srv-2")

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            assertTrue(estado.hayComparacion)
            assertEquals("11.50", estado.rendimientoRealTexto)
            assertEquals("12.00", estado.rendimientoEsperadoTexto)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
