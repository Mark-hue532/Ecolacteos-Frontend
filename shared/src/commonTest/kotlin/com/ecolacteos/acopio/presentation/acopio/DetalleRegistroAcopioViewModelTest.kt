package com.ecolacteos.acopio.presentation.acopio

import app.cash.turbine.test
import com.ecolacteos.acopio.data.remote.dto.Rol
import com.ecolacteos.acopio.domain.Sesion
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleRegistroAcopioUseCase
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests de `A-06` (`PROMPT_FASE_08A.md §5`, punto 12). */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleRegistroAcopioViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(fixture: FixtureRepositorios, id: String = "srv-1") = DetalleRegistroAcopioViewModel(
        id = id,
        obtenerDetalleRegistroAcopioUseCase = ObtenerDetalleRegistroAcopioUseCase(fixture.registroAcopioRepository),
        observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
        gestorSesion = fixture.gestorSesion,
    )

    // 12: fechaHora y sincronizadoEn se exponen etiquetadas por separado, nunca como una duracion entre ambas.
    @Test
    fun `fechaHora y sincronizadoEn se exponen por separado -- nunca una duracion`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson(
                """{"id":"srv-1","uuidCliente":"uuid-x","proveedorId":"prov-1","proveedorNombre":"Granja El Establo",
                   |"unidadId":"unidad-1","fechaHora":"2026-09-06T08:00:00","litros":120.50,
                   |"litrosPorVoz":false,"sincronizadoEn":"2026-09-06T21:00:00"}
                """.trimMargin(),
            )
        }
        val viewModel = crearViewModel(fixture)

        val estado = viewModel.uiState.esperarCargaCompleta()
        assertEquals("06/09/2026 08:00", estado.fechaCapturadoTexto)
        assertEquals("06/09/2026 21:00", estado.sincronizadoTexto)
        // El UiState no expone ningun campo derivado de restar las dos fechas (DATA-012).
    }

    @Test
    fun `rol CALIDAD ve la accion de registrar correccion -- ACOPIADOR no`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson(
                """{"id":"srv-1","uuidCliente":"uuid-x","proveedorId":"prov-1","proveedorNombre":"Granja",
                   |"unidadId":"unidad-1","fechaHora":"2026-09-06T08:00:00","litros":120.50,
                   |"litrosPorVoz":false,"sincronizadoEn":"2026-09-06T21:00:00"}
                """.trimMargin(),
            )
        }
        fixture.gestorSesion.loguearComo(GestorSesionFake.SESION_DE_PRUEBA.copy(rol = Rol.CALIDAD))
        val viewModelCalidad = crearViewModel(fixture)
        assertTrue(viewModelCalidad.uiState.esperarCargaCompleta().puedeRegistrarCorreccion)

        fixture.gestorSesion.loguearComo(GestorSesionFake.SESION_DE_PRUEBA.copy(rol = Rol.ACOPIADOR))
        val viewModelAcopiador = crearViewModel(fixture)
        assertFalse(viewModelAcopiador.uiState.esperarCargaCompleta().puedeRegistrarCorreccion)
    }

    @Test
    fun `el punto de entrada de correccion existe pero el destino no esta implementado todavia`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson(
                """{"id":"srv-1","uuidCliente":"uuid-x","proveedorId":"prov-1","proveedorNombre":"Granja",
                   |"unidadId":"unidad-1","fechaHora":"2026-09-06T08:00:00","litros":120.50,
                   |"litrosPorVoz":false,"sincronizadoEn":"2026-09-06T21:00:00"}
                """.trimMargin(),
            )
        }
        fixture.gestorSesion.loguearComo(GestorSesionFake.SESION_DE_PRUEBA.copy(rol = Rol.CALIDAD))
        val viewModel = crearViewModel(fixture)

        viewModel.effect.test {
            viewModel.onEvent(DetalleRegistroAcopioEvent.RegistrarCorreccionPresionado)
            assertEquals(DetalleRegistroAcopioEffect.CorreccionNoDisponibleTodavia, awaitItem())
        }
    }

    @Test
    fun `no encontrado en ninguna fuente muestra el estado vacio -- no un crash`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val viewModel = crearViewModel(fixture, id = "srv-inexistente")

        val estado = viewModel.uiState.esperarCargaCompleta()
        assertFalse(estado.encontrada)
        assertFalse(estado.cargando)
    }
}

/** Ver la nota equivalente en `RutaDelDiaViewModelTest.kt` -- misma razón, mismo patrón. */
private suspend fun kotlinx.coroutines.flow.StateFlow<DetalleRegistroAcopioUiState>.esperarCargaCompleta(): DetalleRegistroAcopioUiState {
    var estado = DetalleRegistroAcopioUiState()
    test {
        estado = awaitItem()
        while (estado.cargando) estado = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return estado
}
