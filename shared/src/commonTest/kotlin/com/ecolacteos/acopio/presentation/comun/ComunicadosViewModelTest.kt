package com.ecolacteos.acopio.presentation.comun

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.model.Comunicado
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests de `S-06` (`PROMPT_FASE_08B.md §6`, puntos 12-13). */
@OptIn(ExperimentalCoroutinesApi::class)
class ComunicadosViewModelTest {

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
        ComunicadosViewModel(
            observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
            confirmacionesRecientesEnMemoria = ConfirmacionesRecientesEnMemoria(),
        ),
    )

    // 12: fecha se formatea como LocalDateTime (dd/MM/yyyy HH:mm) -- trampa #10, nunca como LocalDate (perderia la hora).
    @Test
    fun `formatea fecha como LocalDateTime con hora -- no como LocalDate`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.catalogosLocal.reemplazarComunicados(
            listOf(
                Comunicado(
                    id = "com-1",
                    mensaje = "Corte de ruta el viernes",
                    fecha = LocalDateTime(2026, 9, 6, 16, 20, 0),
                    zonasNombres = listOf("Zona Norte"),
                    actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
                ),
            ),
        )
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            assertEquals("06/09/2026 16:20", estado.items.single().fechaTexto)
        }
    }

    // 13: sin conexion se lee del cache igual, sin entrar en un estado de error.
    @Test
    fun `sin conexion lee del cache y no entra en estado de error`() = runTest {
        val fixture = FixtureRepositorios(conectividad = com.ecolacteos.acopio.synchronization.ConnectivityObserverFake(inicial = false)) {
            responderJson(cuerpoCambiosVacio())
        }
        fixture.catalogosLocal.reemplazarComunicados(
            listOf(
                Comunicado(
                    id = "com-1",
                    mensaje = "Corte de ruta el viernes",
                    fecha = LocalDateTime(2026, 9, 6, 16, 20, 0),
                    zonasNombres = emptyList(),
                    actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
                ),
            ),
        )
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            assertFalse(estado.hayConexion)
            assertEquals(1, estado.items.size, "el cache sigue disponible sin conexion")
            assertTrue(!estado.vacio, "no se muestra vacio -- el cache tiene datos")
        }
    }
}
