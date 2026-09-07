package com.ecolacteos.acopio.presentation.calidad

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleAnalisisCalidadUseCase
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Test de `C-04` (`PROMPT_FASE_08C.md §7`, punto 13). */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleAnalisisCalidadViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    // 13: los parametros nulos se omiten (ni 0 ni "0.00"); un resultado desconocido se expone sin romper el estado.
    @Test
    fun `omite los parametros nulos y expone un resultado desconocido sin romper el estado`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.analisisCalidadPorRegistro("srv-1")) {
                responderJson(
                    """{"id":"an-1","registroAcopioId":"srv-1","folioMuestra":"F-1",
                        "agua":3.20,"aguaAnadida":true,"resultado":"COSA_RARA","creadoEn":"2026-09-05T08:00:00"}""",
                )
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val viewModel = viewModels.registrar(
            DetalleAnalisisCalidadViewModel(
                registroAcopioId = "srv-1",
                obtenerDetalleAnalisisCalidadUseCase = ObtenerDetalleAnalisisCalidadUseCase(fixture.analisisCalidadRepository),
            ),
        )

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            assertEquals(true, estado.encontrado)
            assertEquals("3.20", estado.aguaTexto)
            assertNull(estado.proteinaTexto, "nulo se omite, nunca 0.00")
            assertNull(estado.lactosaTexto)
            assertNull(estado.densidadTexto)
            assertNull(estado.temperaturaTexto)
            assertNull(estado.phTexto)
            assertEquals("Sí", estado.aguaAnadidaTexto)
            assertEquals("UNKNOWN", estado.resultadoTexto, "valor no reconocido -- se expone, no rompe la pantalla")
        }
    }
}
