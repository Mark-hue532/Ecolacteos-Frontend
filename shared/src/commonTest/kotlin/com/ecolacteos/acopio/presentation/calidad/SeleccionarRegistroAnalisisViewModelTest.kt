package com.ecolacteos.acopio.presentation.calidad

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.usecase.ClasificarPadresRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRegistrosDeProveedorUseCase
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.GestorSesionFake
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PROVEEDOR_ID = "prov-1"

/** Tests de `C-02` (`PROMPT_FASE_08C.md §7`, puntos 1-5). */
@OptIn(ExperimentalCoroutinesApi::class)
class SeleccionarRegistroAnalisisViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    private fun crearViewModel(fixture: FixtureRepositorios, proveedorId: String = PROVEEDOR_ID) = viewModels.registrar(
        SeleccionarRegistroAnalisisViewModel(
            proveedorId = proveedorId,
            clasificarPadresRegistroAcopioUseCase = ClasificarPadresRegistroAcopioUseCase(fixture.registroAcopioRepository),
            obtenerRegistrosDeProveedorUseCase = ObtenerRegistrosDeProveedorUseCase(fixture.registroAcopioRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
        ),
    )

    private fun referenciaAjena(id: String, proveedorId: String = PROVEEDOR_ID) = RegistroAcopioReferencia(
        id = id,
        uuidCliente = null,
        proveedorId = proveedorId,
        proveedorNombre = null,
        fechaHora = LocalDateTime(2026, 9, 4, 6, 0, 0),
        litros = Decimal.parseString("80.00"),
        tieneObservacion = false,
        origen = Origen.RESUMEN,
        actualizadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
    )

    private fun registroPropio(uuidCliente: String, serverId: String?, proveedorId: String = PROVEEDOR_ID) = RegistroAcopio(
        uuidCliente = uuidCliente,
        serverId = serverId,
        usuarioId = GestorSesionFake.USUARIO_ID,
        proveedorId = proveedorId,
        unidadId = "unidad-1",
        fechaHora = LocalDateTime(2026, 9, 5, 6, 0, 0),
        litros = Decimal.parseString("120.50"),
        gpsLat = null,
        gpsLng = null,
        motivoObservacionId = null,
        litrosPorVoz = false,
        syncStatus = if (serverId != null) SyncStatus.SYNCED else SyncStatus.PENDING,
        syncAttempts = 0,
        syncError = null,
        nextAttemptAt = null,
        creadoEn = LocalDateTime(2026, 9, 5, 6, 0, 0),
        sincronizadoEn = if (serverId != null) LocalDateTime(2026, 9, 5, 6, 5, 0) else null,
    )

    // 1: clasifica los tres casos, con el aviso solo en el tercero.
    @Test
    fun `clasifica los tres casos de C-02 con el aviso solo en la entrega propia sin sincronizar`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) {
                responderJson("[]")
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.cacheLocal.upsert(referenciaAjena("srv-ajeno"))
        fixture.registrosLocal.insertar(registroPropio("propio-sincronizado", serverId = "srv-propio"))
        fixture.registrosLocal.insertar(registroPropio("propio-pendiente", serverId = null))

        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            assertEquals(3, estado.items.size)
            val ajeno = estado.items.single { it.idUi == "ajeno:srv-ajeno" }
            val propioSync = estado.items.single { it.idUi == "propio:propio-sincronizado" }
            val propioPendiente = estado.items.single { it.idUi == "propio:propio-pendiente" }

            assertNull(ajeno.aviso, "caso 1 -- ajena ya descargada, sin aviso")
            assertNull(propioSync.aviso, "caso 2 -- propia ya sincronizada, sin aviso")
            assertNotNull(propioPendiente.aviso, "caso 3 -- propia sin sincronizar, con aviso")
            assertEquals(AVISO_ANALISIS_RETENIDO_POR_DEPENDENCIA, propioPendiente.aviso)
            // El refresco on-demand (`refrescar()`, propio viewModelScope.launch) sigue emitiendo
            // `actualizando` por separado del `cargando` de la clasificación -- ground truth ya verificada
            // arriba, no hace falta drenar esas transiciones (mismo criterio que HistorialProveedorViewModelTest).
            cancelAndIgnoreRemainingEvents()
        }
    }

    // 2: el aviso ya está en la fila del UiState antes de cualquier evento de navegación -- DATA-003.
    @Test
    fun `avisa al seleccionar -- el aviso ya esta en la fila antes de navegar a C-03`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) {
                responderJson("[]")
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.registrosLocal.insertar(registroPropio("propio-pendiente", serverId = null))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            // El aviso ya llegó con la sola carga de la lista -- ningún ItemSeleccionado se disparó todavía.
            assertNotNull(estado.items.single().aviso)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // 3: estado vacío del caso no cubierto -- sin filas locales ni cacheadas para este proveedor.
    @Test
    fun `estado vacio cuando no hay nada local ni cacheado para el proveedor`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) {
                responderJson("[]")
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()
            assertTrue(estado.vacio)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // 4: deduplicación DATA-013 -- misma entrega en local (con server_id) y en cache -- sobrevive la local.
    @Test
    fun `deduplica DATA-013 -- misma entrega en local y en cache aparece una sola vez y sobrevive la local`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) {
                responderJson("[]")
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.registrosLocal.insertar(registroPropio("propio-1", serverId = "srv-1"))
        fixture.cacheLocal.upsert(referenciaAjena("srv-1"))

        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            assertEquals(1, estado.items.size)
            assertEquals("propio:propio-1", estado.items.single().idUi)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // 5: refresca al abrir una sola vez -- nunca en cada recomposición (trampa #12 / PROMPT_FASE_06.md §10 trampa 2).
    @Test
    fun `refresca el historial del proveedor una sola vez al abrir`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) {
                responderJson("""[{"id":"srv-nuevo","fechaHora":"2026-09-05T06:00:00","litros":80.00,"tieneObservacion":false}]""")
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val viewModel = crearViewModel(fixture)

        // El refresco (ObtenerRegistrosDeProveedorUseCase) corre en su propio viewModelScope.launch, sin
        // relación directa con el UiState -- esperar a que el observador reactivo asiente le da tiempo al
        // dispatcher de test para procesar también esa otra corrutina antes de contar las rutas pedidas
        // (mismo criterio que HistorialProveedorViewModelTest -- ground truth, nunca uiState.value tras el
        // constructor, y cancelAndIgnoreRemainingEvents en vez de drenar transiciones de `actualizando`).
        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()
            // Simula varias "recomposiciones" leyendo el mismo StateFlow repetidas veces -- no debe volver
            // a disparar el refresco (trampa #12 / PROMPT_FASE_06.md §10 trampa 2).
            repeat(3) { viewModel.uiState.value }
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, fixture.cuantasVecesSePidio(Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)))
    }
}
