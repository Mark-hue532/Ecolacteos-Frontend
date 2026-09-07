package com.ecolacteos.acopio.presentation.acopio

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarHistorialProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRegistrosDeProveedorUseCase
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
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val PROVEEDOR_ID = "p1"

/** Tests de `A-05` (`PROMPT_FASE_08A.md §5`, punto 11: `DATA-013`). */
@OptIn(ExperimentalCoroutinesApi::class)
class HistorialProveedorViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(fixture: FixtureRepositorios) = HistorialProveedorViewModel(
        proveedorId = PROVEEDOR_ID,
        observarHistorialProveedorUseCase = ObservarHistorialProveedorUseCase(fixture.registroAcopioRepository),
        obtenerRegistrosDeProveedorUseCase = ObtenerRegistrosDeProveedorUseCase(fixture.registroAcopioRepository),
        observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
    )

    // 11: una entrega presente en local (con server_id) y en cache (mismo id) aparece una sola vez, y la
    // fila que sobrevive es la local.
    @Test
    fun `DATA-013 -- local y cache con el mismo server_id no duplican -- sobrevive la local`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.registrosLocal.insertar(
            RegistroAcopio(
                uuidCliente = "uuid-local", serverId = "srv-1", usuarioId = GestorSesionFake.USUARIO_ID,
                proveedorId = PROVEEDOR_ID, unidadId = "unidad-1", fechaHora = LocalDateTime(2026, 9, 6, 8, 0, 0),
                litros = Decimal.parseString("100.00"), gpsLat = null, gpsLng = null, motivoObservacionId = null,
                litrosPorVoz = false, syncStatus = SyncStatus.SYNCED, syncAttempts = 0, syncError = null,
                nextAttemptAt = null, creadoEn = LocalDateTime(2026, 9, 6, 8, 0, 1),
                sincronizadoEn = LocalDateTime(2026, 9, 6, 8, 5, 0),
            ),
        )
        fixture.cacheLocal.upsert(
            RegistroAcopioReferencia(
                id = "srv-1", uuidCliente = null, proveedorId = PROVEEDOR_ID, proveedorNombre = "Granja El Establo",
                fechaHora = LocalDateTime(2026, 9, 6, 8, 0, 0), litros = Decimal.parseString("100.00"),
                tieneObservacion = false, origen = Origen.RESUMEN, actualizadoEn = LocalDateTime(2026, 9, 6, 8, 6, 0),
            ),
        )
        val viewModel = crearViewModel(fixture)

        val items = viewModel.uiState.value.items
        assertEquals(1, items.size)
        assertEquals("srv-1", items.single().id)
        assertNotNull(items.single().estadoSync) // la que sobrevive es la local (tiene ciclo de sync)
    }

    @Test
    fun `refresca registros del proveedor una sola vez al entrar`() = runTest {
        val fixture = FixtureRepositorios { responderJson("[]") }
        val viewModel = crearViewModel(fixture)

        // El refresco (ObtenerRegistrosDeProveedorUseCase) corre en su propio viewModelScope.launch, sin
        // relación directa con el UiState -- esperar a que el observador reactivo asiente (mismo motivo
        // que RutaDelDiaViewModelTest/DetalleRegistroAcopioViewModelTest) le da tiempo al dispatcher de
        // test para procesar también esa otra corrutina antes de contar las rutas pedidas.
        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, fixture.cuantasVecesSePidio("/api/registros-acopio/proveedor/$PROVEEDOR_ID"))
    }

    @Test
    fun `una fila sin id no navega -- todavia no sincronizo`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.registrosLocal.insertar(
            RegistroAcopio(
                uuidCliente = "uuid-local", serverId = null, usuarioId = GestorSesionFake.USUARIO_ID,
                proveedorId = PROVEEDOR_ID, unidadId = "unidad-1", fechaHora = LocalDateTime(2026, 9, 6, 8, 0, 0),
                litros = Decimal.parseString("100.00"), gpsLat = null, gpsLng = null, motivoObservacionId = null,
                litrosPorVoz = false, syncStatus = SyncStatus.PENDING, syncAttempts = 0, syncError = null,
                nextAttemptAt = null, creadoEn = LocalDateTime(2026, 9, 6, 8, 0, 1), sincronizadoEn = null,
            ),
        )
        val viewModel = crearViewModel(fixture)

        assertEquals(null, viewModel.uiState.value.items.single().id)
        viewModel.effect.test { expectNoEvents() }
    }
}
