package com.ecolacteos.acopio.presentation.acopio

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.model.Comunicado
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.usecase.BuscarProveedorPorNombreUseCase
import com.ecolacteos.acopio.domain.usecase.ConfirmarComunicadoUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.presentation.comun.ComunicadosViewModel
import com.ecolacteos.acopio.presentation.comun.ConfirmacionesRecientesEnMemoria
import com.ecolacteos.acopio.synchronization.ConnectivityObserverFake
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

/** Tests de `A-07` (`PROMPT_FASE_08B.md §6`, puntos 14-15). Online-only, sin cola (`§18.2`, trampa #9). */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmarComunicadoViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    private val proveedor = Proveedor(
        id = "prov-1", nombre = "Granja El Establo", zonaActualId = null, zonaActualNombre = null,
        codigoQr = null, actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
    )

    private fun sembrarComunicado(fixture: FixtureRepositorios) {
        fixture.catalogosLocal.reemplazarComunicados(
            listOf(
                Comunicado(
                    id = "com-1", mensaje = "Corte de ruta el viernes",
                    fecha = LocalDateTime(2026, 9, 6, 16, 20, 0), zonasNombres = emptyList(),
                    actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
                ),
            ),
        )
        fixture.catalogosLocal.reemplazarProveedores(listOf(proveedor))
    }

    private fun crearViewModel(fixture: FixtureRepositorios, comunicadoId: String = "com-1") = viewModels.registrar(
        ConfirmarComunicadoViewModel(
            comunicadoId = comunicadoId,
            confirmarComunicadoUseCase = ConfirmarComunicadoUseCase(fixture.comunicadoConfirmacionRepository),
            buscarProveedorPorNombreUseCase = BuscarProveedorPorNombreUseCase(fixture.catalogoRepository),
            observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
            confirmacionesRecientesEnMemoria = ConfirmacionesRecientesEnMemoria(),
        ),
    )

    // 14: sin conexion la accion queda deshabilitada, no dispara ninguna llamada de red, y no se encola nada.
    @Test
    fun `sin conexion no dispara ninguna llamada de red al confirmar`() = runTest {
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = false)) {
            responderJson(cuerpoCambiosVacio())
        }
        sembrarComunicado(fixture)
        val viewModel = crearViewModel(fixture)

        viewModel.onEvent(ConfirmarComunicadoEvent.ProveedorSeleccionado(proveedor))
        assertFalse(viewModel.uiState.value.puedeConfirmar, "el boton queda deshabilitado sin conexion")

        // Igual se dispara el evento (defensa en profundidad -- no solo confiar en que la UI no deje tocar el boton).
        viewModel.onEvent(ConfirmarComunicadoEvent.ConfirmarPresionado)

        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.confirmarComunicado("com-1")), "DATA-005/§18.2 -- nunca se encola ni se reintenta")
        assertFalse(viewModel.uiState.value.confirmando)
    }

    // 15: con conexion, al confirmar, la marca aparece en el UiState de S-06 (via ConfirmacionesRecientesEnMemoria compartida).
    @Test
    fun `con conexion al confirmar la marca aparece en S-06`() = runTest {
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = true)) {
            responderJson(
                """{"id":"conf-1","proveedorId":"prov-1","proveedorNombre":"Granja El Establo",
                    |"acopiadorId":"usuario-1","acopiadorNombre":"Ana","confirmadoEn":"2026-09-06T09:00:00"}
                """.trimMargin().replace("\n", ""),
            )
        }
        sembrarComunicado(fixture)
        val confirmaciones = ConfirmacionesRecientesEnMemoria()
        val viewModelConfirmar = viewModels.registrar(
            ConfirmarComunicadoViewModel(
                comunicadoId = "com-1",
                confirmarComunicadoUseCase = ConfirmarComunicadoUseCase(fixture.comunicadoConfirmacionRepository),
                buscarProveedorPorNombreUseCase = BuscarProveedorPorNombreUseCase(fixture.catalogoRepository),
                observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
                observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
                confirmacionesRecientesEnMemoria = confirmaciones,
            ),
        )
        val viewModelComunicados = viewModels.registrar(
            ComunicadosViewModel(
                observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
                observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
                confirmacionesRecientesEnMemoria = confirmaciones,
            ),
        )

        viewModelConfirmar.onEvent(ConfirmarComunicadoEvent.ProveedorSeleccionado(proveedor))
        assertTrue(viewModelConfirmar.uiState.value.puedeConfirmar)

        viewModelConfirmar.effect.test {
            viewModelConfirmar.onEvent(ConfirmarComunicadoEvent.ConfirmarPresionado)
            assertEquals(ConfirmarComunicadoEffect.ConfirmadoConExito, awaitItem())
        }

        viewModelComunicados.uiState.test {
            var estado = awaitItem()
            while (estado.cargando || estado.items.none { it.confirmado }) estado = awaitItem()
            assertTrue(estado.items.single { it.id == "com-1" }.confirmado, "S-06 refleja la confirmacion recien hecha en A-07")
        }
    }
}
