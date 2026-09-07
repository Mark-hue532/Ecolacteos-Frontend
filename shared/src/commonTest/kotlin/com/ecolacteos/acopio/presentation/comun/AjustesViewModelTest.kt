package com.ecolacteos.acopio.presentation.comun

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarEstadoSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarPendientesUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarResumenSyncUseCase
import com.ecolacteos.acopio.domain.usecase.SincronizarAhoraUseCase
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.synchronization.GestorSesionFake
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests de `S-07` (`PROMPT_FASE_08B.md §6`, puntos 7-11). El árbol de decisión en sí ya lo cubre `MultiusuarioYLogoutTest` a nivel `LogoutUseCase` (Fase 6) -- acá se prueba lo que `AjustesViewModel` agrega: `UiState`, diálogos y la reevaluación reactiva. */
@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModelTest {

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
        AjustesViewModel(
            gestorSesion = fixture.gestorSesion,
            logoutUseCase = fixture.logout,
            observarPendientesUseCase = ObservarPendientesUseCase(
                fixture.registroAcopioRepository, fixture.analisisCalidadRepository, fixture.loteProduccionRepository, fixture.ventaRepository,
            ),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
            observarResumenSyncUseCase = ObservarResumenSyncUseCase(
                ObservarPendientesUseCase(
                    fixture.registroAcopioRepository, fixture.analisisCalidadRepository, fixture.loteProduccionRepository, fixture.ventaRepository,
                ),
                fixture.catalogoRepository,
            ),
            observarEstadoSyncUseCase = ObservarEstadoSyncUseCase(fixture.syncEngine),
            sincronizarAhoraUseCase = SincronizarAhoraUseCase(fixture.syncEngine),
        ),
    )

    private fun registroPendiente(uuidCliente: String, usuarioId: String = GestorSesionFake.USUARIO_ID) = RegistroAcopio(
        uuidCliente = uuidCliente,
        serverId = null,
        usuarioId = usuarioId,
        proveedorId = "prov-1",
        unidadId = "unidad-1",
        fechaHora = LocalDateTime(2026, 9, 6, 6, 0, 0),
        litros = Decimal.parseString("120.50"),
        gpsLat = null,
        gpsLng = null,
        motivoObservacionId = null,
        litrosPorVoz = false,
        syncStatus = SyncStatus.PENDING,
        syncAttempts = 0,
        syncError = null,
        nextAttemptAt = null,
        creadoEn = LocalDateTime(2026, 9, 6, 6, 0, 1),
        sincronizadoEn = null,
    )

    // 7: con pendientes propios, el logout no procede -- UiState expone el conteo y el dialogo de bloqueo.
    @Test
    fun `logout bloqueado -- con pendientes expone el conteo y el dialogo con las 4 opciones`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.registrosLocal.insertar(registroPendiente("reg-1"))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            viewModel.onEvent(AjustesEvent.CerrarSesionPresionado)
            while (!estado.mostrarDialogoBloqueado) estado = awaitItem()

            assertEquals(1, estado.pendientesConteo)
        }
        assertNotNull(fixture.gestorSesion.sesion.value, "bloqueado -- la sesion no se invalida")
    }

    // 8: sin pendientes, el logout procede -- navega a Login e invalida la sesion.
    @Test
    fun `logout limpio -- sin pendientes navega a Login y borra la sesion`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        val viewModel = crearViewModel(fixture)

        viewModel.effect.test {
            viewModel.onEvent(AjustesEvent.CerrarSesionPresionado)
            assertEquals(AjustesEffect.NavegarALogin, awaitItem())
        }
        assertNull(fixture.gestorSesion.sesion.value, "logout limpio invalida la sesion")
    }

    // 9: "cerrar sesion igual" borra token y caches personales, pero nunca las filas *_local no sincronizadas.
    @Test
    fun `cerrar sesion igual conserva las filas pendientes y borra solo la sesion`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.registrosLocal.insertar(registroPendiente("reg-1"))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            viewModel.onEvent(AjustesEvent.CerrarSesionPresionado)
            while (!estado.mostrarDialogoBloqueado) estado = awaitItem()

            viewModel.onEvent(AjustesEvent.CerrarSesionIgualPresionado)
            estado = awaitItem()
            assertTrue(estado.mostrarConfirmacionCerrarIgual, "segunda confirmacion explicita antes de perder de vista los pendientes")
        }

        viewModel.effect.test {
            viewModel.onEvent(AjustesEvent.ConfirmarCerrarIgualPresionado)
            assertEquals(AjustesEffect.NavegarALogin, awaitItem())
        }

        assertNull(fixture.gestorSesion.sesion.value, "el token se borra igual")
        val fila = fixture.registrosLocal.obtenerPorUuidCliente("reg-1")
        assertEquals(SyncStatus.PENDING, fila?.syncStatus, "CLAUDE.md §3.6 -- nunca se borra trabajo no confirmado, ni con logout explicito")
    }

    // 10: "al terminar, reevaluar" -- si tras sincronizar quedan filas FAILED, el dialogo de bloqueo sigue.
    @Test
    fun `reevalua tras sincronizar -- si queda una fila FAILED el dialogo de bloqueo no se cierra solo`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson("""{"mensaje":"fecha fuera de tolerancia"}""", HttpStatusCode.UnprocessableEntity)
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.registrosLocal.insertar(registroPendiente("reg-1"))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            viewModel.onEvent(AjustesEvent.CerrarSesionPresionado)
            while (!estado.mostrarDialogoBloqueado) estado = awaitItem()

            viewModel.onEvent(AjustesEvent.SincronizarAhoraPresionado)
            // Verdad de terreno (la fila en SQLite), no el parpadeo de `sincronizando`/`pendientesConteo` del
            // combine() -- el ciclo toca registros y catalogos por separado y puede emitir de mas.
            while (fixture.registrosLocal.obtenerPorUuidCliente("reg-1")?.syncStatus != SyncStatus.FAILED) estado = awaitItem()

            cancelAndIgnoreRemainingEvents()
        }

        // El 422 es permanente -- la fila pasa a FAILED, sigue contando como pendiente: el dialogo no se cierra solo.
        assertTrue(viewModel.uiState.value.mostrarDialogoBloqueado, "sigue bloqueado -- no dejo salir creyendo que se envio todo")
        assertNotNull(fixture.gestorSesion.sesion.value)
        assertEquals(SyncStatus.FAILED, fixture.registrosLocal.obtenerPorUuidCliente("reg-1")?.syncStatus)
    }

    // 11: multiusuario -- los pendientes de OTRO usuario_id no cuentan para la sesion activa, el logout procede.
    @Test
    fun `multiusuario -- pendientes de otro usuario no bloquean el logout de la sesion activa`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.registrosLocal.insertar(registroPendiente("reg-de-otro", usuarioId = GestorSesionFake.USUARIO_ID_B))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.pendientesConteo != 0) estado = awaitItem()
            assertEquals(0, estado.pendientesConteo, "la sesion activa (usuario A) no ve los pendientes de B")
        }

        viewModel.effect.test {
            viewModel.onEvent(AjustesEvent.CerrarSesionPresionado)
            assertEquals(AjustesEffect.NavegarALogin, awaitItem())
        }
        assertNull(fixture.gestorSesion.sesion.value, "el logout de A procede sin que B lo bloquee")
    }
}
