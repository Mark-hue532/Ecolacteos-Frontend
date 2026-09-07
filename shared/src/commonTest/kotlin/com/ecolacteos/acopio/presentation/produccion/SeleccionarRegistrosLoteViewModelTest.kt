package com.ecolacteos.acopio.presentation.produccion

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.ClasificarPadresRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearLoteProduccionUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRegistrosDeProveedorUseCase
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.GestorSesionFake
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PROVEEDOR_ID = "prov-1"

/** Tests de `P-02` (`PROMPT_FASE_08D.md §8`, puntos 1-6). */
class SeleccionarRegistrosLoteViewModelTest {

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
        SeleccionarRegistrosLoteViewModel(
            proveedorId = proveedorId,
            clasificarPadresRegistroAcopioUseCase = ClasificarPadresRegistroAcopioUseCase(fixture.registroAcopioRepository),
            obtenerRegistrosDeProveedorUseCase = ObtenerRegistrosDeProveedorUseCase(fixture.registroAcopioRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
        ),
    )

    private fun referenciaAjena(id: String, litros: String = "80.00", proveedorId: String = PROVEEDOR_ID) = RegistroAcopioReferencia(
        id = id,
        uuidCliente = null,
        proveedorId = proveedorId,
        proveedorNombre = null,
        fechaHora = LocalDateTime(2026, 9, 4, 6, 0, 0),
        litros = Decimal.parseString(litros),
        tieneObservacion = false,
        origen = Origen.RESUMEN,
        actualizadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
    )

    private fun registroPropio(uuidCliente: String, serverId: String?, litros: String = "50.00", proveedorId: String = PROVEEDOR_ID) = RegistroAcopio(
        uuidCliente = uuidCliente,
        serverId = serverId,
        usuarioId = GestorSesionFake.USUARIO_ID,
        proveedorId = proveedorId,
        unidadId = "unidad-1",
        fechaHora = LocalDateTime(2026, 9, 5, 6, 0, 0),
        litros = Decimal.parseString(litros),
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

    // 1: seleccionar y deseleccionar mantiene el conjunto correcto; 0 seleccionados no permite continuar.
    @Test
    fun `seleccionar y deseleccionar mantiene el conjunto correcto`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) responderJson("[]") else responderJson(cuerpoCambiosVacio())
        }
        fixture.cacheLocal.upsert(referenciaAjena("srv-1"))
        fixture.cacheLocal.upsert(referenciaAjena("srv-2"))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()
            assertFalse(estado.puedeContinuar, "sin seleccion no se puede continuar")

            viewModel.onEvent(SeleccionarRegistrosLoteEvent.ItemToggled("ajeno:srv-1"))
            estado = awaitItem()
            assertTrue(estado.puedeContinuar)
            assertTrue(estado.items.single { it.idUi == "ajeno:srv-1" }.seleccionado)
            assertFalse(estado.items.single { it.idUi == "ajeno:srv-2" }.seleccionado)

            viewModel.onEvent(SeleccionarRegistrosLoteEvent.ItemToggled("ajeno:srv-1"))
            estado = awaitItem()
            assertFalse(estado.puedeContinuar, "al deseleccionar la unica fila, vuelve a no poder continuar")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // 2: retencion agregada -- con 1 de 5 propia-sin-sincronizar, el lote queda marcado como retenido con conteo "1 de 5".
    @Test
    fun `retencion agregada -- una sola entrega sin sincronizar retiene el lote entero`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) responderJson("[]") else responderJson(cuerpoCambiosVacio())
        }
        fixture.cacheLocal.upsert(referenciaAjena("srv-1"))
        fixture.cacheLocal.upsert(referenciaAjena("srv-2"))
        fixture.cacheLocal.upsert(referenciaAjena("srv-3"))
        fixture.registrosLocal.insertar(registroPropio("propio-sync", serverId = "srv-4"))
        fixture.registrosLocal.insertar(registroPropio("propio-pendiente", serverId = null))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()
            assertEquals(5, estado.items.size)

            // Selecciona las 5 -- una es propia sin sincronizar.
            listOf("ajeno:srv-1", "ajeno:srv-2", "ajeno:srv-3", "propio:propio-sync", "propio:propio-pendiente").forEach {
                viewModel.onEvent(SeleccionarRegistrosLoteEvent.ItemToggled(it))
                estado = awaitItem()
            }
            val aviso = estado.avisoRetencion
            assertNotNull(aviso, "1 de 5 sin sincronizar -- el lote entero queda retenido")
            assertTrue(aviso.contains("1 de las 5"))

            // Deselecciona la propia sin sincronizar -- ya no hay aviso.
            viewModel.onEvent(SeleccionarRegistrosLoteEvent.ItemToggled("propio:propio-pendiente"))
            estado = awaitItem()
            assertNull(estado.avisoRetencion, "sin ninguna propia-sin-sincronizar, no hay aviso")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // 3: el aviso ya esta en el UiState antes de cualquier evento de navegacion a P-03 (DATA-003).
    @Test
    fun `avisa al seleccionar -- el aviso ya esta antes de continuar a P-03`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) responderJson("[]") else responderJson(cuerpoCambiosVacio())
        }
        fixture.registrosLocal.insertar(registroPropio("propio-pendiente", serverId = null))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            viewModel.onEvent(SeleccionarRegistrosLoteEvent.ItemToggled("propio:propio-pendiente"))
            estado = awaitItem()
            // El aviso ya esta -- ContinuarPresionado todavia no se disparo.
            assertNotNull(estado.avisoRetencion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // 4: se expone el total de lo seleccionado, y P-03 no lo autocompleta en litrosUsados.
    @Test
    fun `expone el total de lo seleccionado sin autocompletar litrosUsados en P-03`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) responderJson("[]") else responderJson(cuerpoCambiosVacio())
        }
        fixture.cacheLocal.upsert(referenciaAjena("srv-1", litros = "80.00"))
        fixture.cacheLocal.upsert(referenciaAjena("srv-2", litros = "40.50"))
        val viewModel = crearViewModel(fixture)

        var totalTexto = ""
        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()
            assertEquals("0.00", estado.totalLitrosSeleccionadoTexto)

            viewModel.onEvent(SeleccionarRegistrosLoteEvent.ItemToggled("ajeno:srv-1"))
            estado = awaitItem()
            viewModel.onEvent(SeleccionarRegistrosLoteEvent.ItemToggled("ajeno:srv-2"))
            estado = awaitItem()
            assertEquals("120.50", estado.totalLitrosSeleccionadoTexto)
            totalTexto = estado.totalLitrosSeleccionadoTexto
            cancelAndIgnoreRemainingEvents()
        }

        // El total de P-02 nunca autocompleta litrosUsadosTexto en P-03 -- solo se pasa como ayuda.
        val registrarLote = viewModels.registrar(
            RegistrarLoteViewModel(
                registroAcopioUuidClientes = emptyList(),
                registroAcopioServerIds = listOf("srv-1", "srv-2"),
                totalLitrosSeleccionadoTexto = totalTexto,
                crearLoteProduccionUseCase = CrearLoteProduccionUseCase(fixture.loteProduccionRepository),
                observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
                observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
                borradorFormularioUseCase = BorradorFormularioUseCase(fixture.borradorFormularioRepository),
            ),
        )
        assertEquals(totalTexto, registrarLote.uiState.value.totalLitrosSeleccionadoTexto, "el total se muestra")
        assertEquals("", registrarLote.uiState.value.litrosUsadosTexto, "pero nunca se autocompleta")
    }

    // 5: DATA-013 -- misma entrega en local (con server_id) y en cache aparece una sola vez, sobrevive la local.
    @Test
    fun `deduplica DATA-013 -- misma entrega en local y cache aparece una sola vez`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) responderJson("[]") else responderJson(cuerpoCambiosVacio())
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

    // 6: no permite el mismo registro dos veces en el lote -- togglear la misma fila dos veces la deja fuera, no duplicada.
    @Test
    fun `no permite el mismo registro dos veces -- togglear dos veces lo deja deseleccionado`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID)) responderJson("[]") else responderJson(cuerpoCambiosVacio())
        }
        fixture.cacheLocal.upsert(referenciaAjena("srv-1"))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            viewModel.onEvent(SeleccionarRegistrosLoteEvent.ItemToggled("ajeno:srv-1"))
            estado = awaitItem()
            assertEquals(1, estado.items.count { it.seleccionado })

            viewModel.onEvent(SeleccionarRegistrosLoteEvent.ItemToggled("ajeno:srv-1"))
            estado = awaitItem()
            assertEquals(0, estado.items.count { it.seleccionado }, "el Set no permite un segundo registro de la misma fila")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
