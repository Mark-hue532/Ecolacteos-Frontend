package com.ecolacteos.acopio.presentation.acopio

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ResolverProveedorPorQrUseCase
import com.ecolacteos.acopio.plataforma.EstadoPermiso
import com.ecolacteos.acopio.plataforma.GestorPermisosFake
import com.ecolacteos.acopio.plataforma.Permiso
import com.ecolacteos.acopio.synchronization.responderJson
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertTrue

/** Tests de `A-02` (`PROMPT_FASE_08A.md §5`, puntos 8, 9, 13). */
@OptIn(ExperimentalCoroutinesApi::class)
class EscanearQrViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(fixture: FixtureRepositorios, permisos: GestorPermisosFake) = EscanearQrViewModel(
        resolverProveedorPorQrUseCase = ResolverProveedorPorQrUseCase(fixture.catalogoRepository),
        gestorPermisos = permisos,
    )

    // 8: un QR presente en proveedor_cache no dispara ninguna llamada de red (a nivel ViewModel).
    @Test
    fun `codigo en cache resuelve y navega sin tocar la red`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.catalogosLocal.reemplazarProveedores(
            listOf(
                Proveedor(
                    id = "p1", nombre = "Granja El Establo", zonaActualId = "zona-1", zonaActualNombre = "Zona 1",
                    codigoQr = "QR-1", actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
                ),
            ),
        )
        val viewModel = crearViewModel(fixture, GestorPermisosFake(Permiso.CAMARA))

        viewModel.effect.test {
            viewModel.onEvent(EscanearQrEvent.CodigoDetectado("QR-1"))
            assertEquals(EscanearQrEffect.NavegarARegistrar("p1"), awaitItem())
        }
        assertTrue(fixture.rutasPedidas.isEmpty())
    }

    // 9: sin cache y sin conexión -- mensaje + navega a A-03, sin reintento ciego.
    @Test
    fun `sin cache y sin conexion muestra el mensaje y navega a buscar`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val viewModel = crearViewModel(fixture, GestorPermisosFake(Permiso.CAMARA))

        viewModel.effect.test {
            viewModel.onEvent(EscanearQrEvent.CodigoDetectado("QR-desconocido"))
            assertEquals(EscanearQrEffect.NavegarABuscar, awaitItem())
        }
        assertTrue(viewModel.uiState.value.mensaje!!.contains("no hay señal"))
    }

    // 9: sin cache, con conexión, 404 real -- mensaje de "no corresponde", sin navegar.
    @Test
    fun `sin cache y 404 real muestra que el codigo no corresponde a ningun proveedor`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.NotFound) }
        val viewModel = crearViewModel(fixture, GestorPermisosFake(Permiso.CAMARA))

        viewModel.onEvent(EscanearQrEvent.CodigoDetectado("QR-desconocido"))
        // resolverCodigo corre en viewModelScope.launch -- esperar a que "resolviendo" vuelva a false
        // (vía el propio UiState) evita leer el mensaje antes de que la llamada de red del fallback
        // resuelva (mismo motivo que RutaDelDiaViewModelTest/DetalleRegistroAcopioViewModelTest).
        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.resolviendo) estado = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.effect.test { expectNoEvents() }
        assertEquals("Este código no corresponde a ningún proveedor", viewModel.uiState.value.mensaje)
    }

    // 13: denegación permanente produce el estado con salida a ajustes y no vuelve a disparar el diálogo.
    @Test
    fun `denegacion permanente no vuelve a disparar el dialogo por su cuenta`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val permisos = GestorPermisosFake()
        val viewModel = crearViewModel(fixture, permisos)

        assertEquals(EstadoPermiso.NO_DETERMINADO, viewModel.uiState.value.estadoPermiso)

        viewModel.onEvent(EscanearQrEvent.PermisoResuelto(EstadoPermiso.DENEGADO_PERMANENTE))
        assertEquals(EstadoPermiso.DENEGADO_PERMANENTE, viewModel.uiState.value.estadoPermiso)

        viewModel.onEvent(EscanearQrEvent.IrAAjustesPresionado)
        assertEquals(1, permisos.vecesAbrioAjustes)
        // El ViewModel nunca vuelve a emitir SolicitarPermiso por su cuenta -- solo un tap explícito lo hace.
        viewModel.effect.test { expectNoEvents() }
    }
}
