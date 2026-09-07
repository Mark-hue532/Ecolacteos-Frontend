package com.ecolacteos.acopio.presentation.calidad

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.usecase.BuscarAnalisisPorFolioUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.ConnectivityObserverFake
import com.ecolacteos.acopio.synchronization.responderJson
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests de `C-05` (`PROMPT_FASE_08E.md §8`, punto 9). ONLINE-ONLY. */
@OptIn(ExperimentalCoroutinesApi::class)
class BuscarAnalisisPorFolioViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    private fun crearViewModel(fixture: FixtureRepositorios) = viewModels.registrar(
        BuscarAnalisisPorFolioViewModel(
            buscarAnalisisPorFolioUseCase = BuscarAnalisisPorFolioUseCase(fixture.analisisCalidadRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
        ),
    )

    // 9a: un folio de 40 caracteres es valido y la busqueda se dispara.
    @Test
    fun `folio de 40 caracteres es valido y dispara la busqueda`() = runTest {
        val folio40 = "F".repeat(40)
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = true)) {
            if (it.url.encodedPath == Endpoints.analisisCalidadPorFolio(folio40)) {
                responderJson(
                    """{"id":"an-1","registroAcopioId":"srv-1","folioMuestra":"$folio40",
                        "agua":3.20,"aguaAnadida":false,"resultado":"APROBADO","creadoEn":"2026-09-05T08:00:00"}""",
                )
            } else {
                error("no deberia pedirse otra ruta")
            }
        }
        val viewModel = crearViewModel(fixture)

        viewModel.onEvent(BuscarAnalisisPorFolioEvent.FolioCambio(folio40))
        assertTrue(viewModel.uiState.value.puedeBuscar, "40 caracteres es el limite valido")

        val estado = viewModel.uiState.esperarBusquedaCompleta { viewModel.onEvent(BuscarAnalisisPorFolioEvent.BuscarPresionado) }

        assertEquals(1, fixture.cuantasVecesSePidio(Endpoints.analisisCalidadPorFolio(folio40)))
        assertEquals(folio40, estado.resultado?.folioMuestraTexto)
    }

    // 9b: sin resultados es un estado vacio -- nunca un error.
    @Test
    fun `sin resultados produce estado vacio -- no error`() = runTest {
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = true)) {
            respondError(HttpStatusCode.NotFound)
        }
        val viewModel = crearViewModel(fixture)

        viewModel.onEvent(BuscarAnalisisPorFolioEvent.FolioCambio("F-INEXISTENTE"))
        val estado = viewModel.uiState.esperarBusquedaCompleta { viewModel.onEvent(BuscarAnalisisPorFolioEvent.BuscarPresionado) }

        assertTrue(estado.sinResultados)
        assertNull(estado.mensajeError, "un 404 de negocio no es lo mismo que un fallo de red")
        assertNull(estado.resultado)
    }

    // 9c: sin conexion el campo/boton quedan deshabilitados -- no solo bloqueados al enviar.
    @Test
    fun `sin conexion queda deshabilitado`() = runTest {
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = false)) {
            responderJson(
                """{"id":"an-1","registroAcopioId":"srv-1","folioMuestra":"F-1","aguaAnadida":false,
                    "resultado":"APROBADO","creadoEn":"2026-09-05T08:00:00"}""",
            )
        }
        val viewModel = crearViewModel(fixture)

        viewModel.onEvent(BuscarAnalisisPorFolioEvent.FolioCambio("F-1"))
        assertFalse(viewModel.uiState.value.puedeBuscar, "sin conexion, deshabilitado aunque el folio sea valido")

        viewModel.onEvent(BuscarAnalisisPorFolioEvent.BuscarPresionado)
        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.analisisCalidadPorFolio("F-1")), "defensa en profundidad -- tampoco dispara si igual se llama al evento")
    }
}

/**
 * Espera a que termine la busqueda antes de leer el resultado -- no `uiState.value` justo despues de
 * disparar el evento, sino con `uiState.test{}` + `awaitItem()` (leccion de `8A`, ver §8 del prompt).
 * `buscando` arranca en `false` (a diferencia de `cargando` en las pantallas de detalle, que arranca en
 * `true`), asi que un `while (buscando)` a secas no alcanza -- podria "terminar" leyendo el snapshot de
 * ANTES de disparar el evento. Verdad de terreno (leccion de `8B`): se compara contra el estado inicial en
 * vez de contar transiciones, asi no importa si `buscando=true` se pierde por conflation de `StateFlow`.
 */
private suspend fun StateFlow<BuscarAnalisisPorFolioUiState>.esperarBusquedaCompleta(
    disparar: () -> Unit,
): BuscarAnalisisPorFolioUiState {
    val estadoInicial = value
    var estado = estadoInicial
    test {
        disparar()
        estado = awaitItem()
        while (estado == estadoInicial || estado.buscando) estado = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return estado
}
