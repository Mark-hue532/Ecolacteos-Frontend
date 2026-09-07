package com.ecolacteos.acopio.presentation.comun

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.data.repository.NuevoRegistroAcopio
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.Venta
import com.ecolacteos.acopio.domain.usecase.DescartarPendienteUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarEstadoSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarPendientesUseCase
import com.ecolacteos.acopio.domain.usecase.ReintentarManualUseCase
import com.ecolacteos.acopio.domain.usecase.SincronizarAhoraUseCase
import com.ecolacteos.acopio.synchronization.GestorSesionFake
import com.ecolacteos.acopio.synchronization.RecursoSync
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ZONA = TimeZone.UTC

/** Tests de `S-05` (`PROMPT_FASE_08B.md §6`, puntos 1-6). */
@OptIn(ExperimentalCoroutinesApi::class)
class PendientesViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(fixture: FixtureRepositorios) = PendientesViewModel(
        observarPendientesUseCase = ObservarPendientesUseCase(
            fixture.registroAcopioRepository, fixture.analisisCalidadRepository, fixture.loteProduccionRepository, fixture.ventaRepository,
        ),
        observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
        observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
        observarEstadoSyncUseCase = ObservarEstadoSyncUseCase(fixture.syncEngine),
        reintentarManualUseCase = ReintentarManualUseCase(
            fixture.registroAcopioRepository, fixture.analisisCalidadRepository, fixture.loteProduccionRepository, fixture.ventaRepository,
        ),
        descartarPendienteUseCase = DescartarPendienteUseCase(
            fixture.registroAcopioRepository, fixture.analisisCalidadRepository, fixture.loteProduccionRepository, fixture.ventaRepository,
        ),
        sincronizarAhoraUseCase = SincronizarAhoraUseCase(fixture.syncEngine),
        reloj = fixture.reloj,
        zona = ZONA,
    )

    private fun registro(
        uuidCliente: String,
        estado: SyncStatus,
        syncError: String? = null,
        intentos: Int = 0,
        creadoEn: LocalDateTime = LocalDateTime(2026, 9, 6, 6, 0, 0),
        usuarioId: String = GestorSesionFake.USUARIO_ID,
    ) = RegistroAcopio(
        uuidCliente = uuidCliente,
        serverId = null,
        usuarioId = usuarioId,
        proveedorId = "prov-1",
        unidadId = "unidad-1",
        fechaHora = LocalDateTime(2026, 9, 5, 6, 0, 0),
        litros = Decimal.parseString("120.50"),
        gpsLat = null,
        gpsLng = null,
        motivoObservacionId = null,
        litrosPorVoz = false,
        syncStatus = estado,
        syncAttempts = intentos,
        syncError = syncError,
        nextAttemptAt = if (estado == SyncStatus.FAILED) LocalDateTime(2026, 9, 6, 13, 0, 0) else null,
        creadoEn = creadoEn,
        sincronizadoEn = null,
    )

    private fun venta(uuidCliente: String, estado: SyncStatus) = Venta(
        uuidCliente = uuidCliente,
        serverId = null,
        usuarioId = GestorSesionFake.USUARIO_ID,
        fecha = LocalDate(2026, 9, 5),
        tipoCliente = TipoClienteVenta.MAYORISTA,
        tipoQuesoId = "queso-1",
        cantidad = 10,
        precioUnitario = Decimal.parseString("25.50"),
        syncStatus = estado,
        syncAttempts = 0,
        syncError = null,
        nextAttemptAt = null,
        creadoEn = LocalDateTime(2026, 9, 6, 9, 0, 0),
        sincronizadoEn = null,
    )

    // 1: FAILED arriba, PENDING_DEPENDENCY en el medio, PENDING/SYNCING abajo -- 2 recursos distintos.
    @Test
    fun `agrupa en las tres secciones en el orden correcto con recursos distintos`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.registrosLocal.insertar(registro("reg-error", SyncStatus.FAILED, syncError = "proveedor inactivo"))
        fixture.registrosLocal.insertar(registro("reg-espera", SyncStatus.PENDING_DEPENDENCY, syncError = "DATA-014"))
        fixture.ventasLocal.insertar(venta("venta-pendiente", SyncStatus.PENDING))
        fixture.ventasLocal.insertar(venta("venta-en-vuelo", SyncStatus.SYNCING))

        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            assertEquals(listOf("reg-error"), estado.conError.map { it.uuidCliente })
            assertEquals(listOf("reg-espera"), estado.esperandoDependencia.map { it.uuidCliente })
            assertEquals(setOf("venta-pendiente", "venta-en-vuelo"), estado.porEnviar.map { it.uuidCliente }.toSet())
        }
    }

    // 2: el motivo del backend se muestra literal, sin reinterpretar.
    @Test
    fun `muestra el motivo del backend literal sin traducir`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.registrosLocal.insertar(registro("reg-error", SyncStatus.FAILED, syncError = "fecha fuera de tolerancia"))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            assertEquals("fecha fuera de tolerancia", estado.conError.single().motivoError)
        }
    }

    // 3: PENDING_DEPENDENCY no es un error -- categoria distinta, y el motivo DATA-014 se muestra tal cual.
    @Test
    fun `PENDING_DEPENDENCY no es un error y expone el motivo DATA-014 tal cual`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        val motivo = "DATA-014: sin server_id del padre, no se puede resolver registroAcopioId"
        fixture.registrosLocal.insertar(registro("reg-espera", SyncStatus.PENDING_DEPENDENCY, syncError = motivo))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            val item = estado.esperandoDependencia.single()
            assertTrue(estado.conError.isEmpty(), "PENDING_DEPENDENCY nunca cuenta como error")
            val esperando = assertIs<EstadoSincronizacion.EsperandoDependencia>(item.estado)
            assertEquals(motivo, esperando.motivoConocido)
        }
    }

    // 4: diasEsperando > 3 produce la advertencia, <= 3 no.
    @Test
    fun `diasEsperando mayor a 3 dias produce advertencia -- 3 o menos no`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        // reloj fijo en 2026-09-06T12:00:00Z (FixtureRepositorios) -- 4 dias antes vs 2 dias antes.
        fixture.registrosLocal.insertar(registro("reg-viejo", SyncStatus.FAILED, syncError = "x", creadoEn = LocalDateTime(2026, 9, 2, 6, 0, 0)))
        fixture.registrosLocal.insertar(registro("reg-reciente", SyncStatus.FAILED, syncError = "x", creadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0)))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            val viejo = estado.conError.single { it.uuidCliente == "reg-viejo" }
            val reciente = estado.conError.single { it.uuidCliente == "reg-reciente" }
            assertTrue(viejo.diasEsperando > 3)
            assertTrue(viejo.esperaConAdvertencia)
            assertTrue(reciente.diasEsperando <= 3)
            assertTrue(!reciente.esperaConAdvertencia)
        }
    }

    // 5a: descartar exige confirmacion explicita y borra solo la fila objetivo.
    @Test
    fun `descartar exige confirmacion y borra solo la fila objetivo`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.registrosLocal.insertar(registro("descartar-1", SyncStatus.FAILED, syncError = "x"))
        fixture.registrosLocal.insertar(registro("descartar-2", SyncStatus.FAILED, syncError = "x"))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            val item1 = estado.conError.single { it.uuidCliente == "descartar-1" }
            viewModel.onEvent(PendientesEvent.DescartarPresionado(item1))
            estado = awaitItem()
            assertEquals(item1, estado.pendienteADescartar, "el dialogo queda abierto con la fila exacta")
            assertNotNull(fixture.registrosLocal.obtenerPorUuidCliente("descartar-1"), "no borra sin confirmar")

            viewModel.onEvent(PendientesEvent.ConfirmarDescartePresionado)
            while (estado.pendienteADescartar != null || estado.conError.any { it.uuidCliente == "descartar-1" }) estado = awaitItem()
        }

        assertNull(fixture.registrosLocal.obtenerPorUuidCliente("descartar-1"), "la fila objetivo se borro")
        assertNotNull(fixture.registrosLocal.obtenerPorUuidCliente("descartar-2"), "la otra fila queda intacta")
    }

    // 5b: descartar filtra por usuario_id -- nunca borra trabajo no confirmado de otro usuario.
    @Test
    fun `descartar no toca filas de otro usuario_id`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.registrosLocal.insertar(registro("de-otro-usuario", SyncStatus.FAILED, syncError = "x", usuarioId = GestorSesionFake.USUARIO_ID_B))
        val descartar = DescartarPendienteUseCase(
            fixture.registroAcopioRepository, fixture.analisisCalidadRepository, fixture.loteProduccionRepository, fixture.ventaRepository,
        )

        // Sesion activa es USUARIO_ID (default del fixture) -- intenta descartar una fila de USUARIO_ID_B.
        descartar(RecursoSync.REGISTRO_ACOPIO, "de-otro-usuario")

        assertNotNull(fixture.registrosLocal.obtenerPorUuidCliente("de-otro-usuario"), "una tablet compartida no borra trabajo ajeno")
    }

    // 6a: editar navega a A-04 en modo edicion con el uuidCliente exacto.
    @Test
    fun `editar un registro de acopio navega a A-04 con el mismo uuidCliente`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.registrosLocal.insertar(registro("reg-editar", SyncStatus.FAILED, syncError = "x"))
        val viewModel = crearViewModel(fixture)

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()
            val item = estado.conError.single()

            viewModel.effect.test {
                viewModel.onEvent(PendientesEvent.EditarPresionado(item))
                assertEquals(PendientesEffect.NavegarAEditarAcopio("reg-editar"), awaitItem())
            }
        }
    }

    // 6b: editar y reintentar vuelve la fila a PENDING, resetea sync_attempts, y conserva el uuidCliente (§7 idempotencia).
    @Test
    fun `editar y reintentar vuelve la fila a PENDING resetea intentos y conserva el uuidCliente`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.registrosLocal.insertar(registro("reg-editar", SyncStatus.FAILED, syncError = "proveedor inactivo", intentos = 3))

        val actualizado = fixture.registroAcopioRepository.actualizar(
            "reg-editar",
            NuevoRegistroAcopio(
                proveedorId = "prov-1",
                unidadId = "unidad-1",
                fechaHora = LocalDateTime(2026, 9, 6, 7, 0, 0),
                litros = Decimal.parseString("130.00"),
                gpsLat = null,
                gpsLng = null,
                motivoObservacionId = null,
                litrosPorVoz = false,
            ),
        )

        assertTrue(actualizado)
        val fila = fixture.registrosLocal.obtenerPorUuidCliente("reg-editar")
        assertEquals("reg-editar", fila?.uuidCliente, "trampa #8 -- nunca cambia el uuidCliente")
        assertEquals(SyncStatus.PENDING, fila?.syncStatus)
        assertEquals(0, fila?.syncAttempts)
        assertNull(fila?.syncError)
    }
}
