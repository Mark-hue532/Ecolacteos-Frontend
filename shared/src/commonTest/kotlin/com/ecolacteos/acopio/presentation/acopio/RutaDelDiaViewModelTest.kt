package com.ecolacteos.acopio.presentation.acopio

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.Unidad
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarProveedoresVisitadosHoyUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRutaDelDiaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerZonaAsignadaUseCase
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
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests de `A-01` (`PROMPT_FASE_08A.md §5`, punto 10). */
@OptIn(ExperimentalCoroutinesApi::class)
class RutaDelDiaViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sembrarUnidadPropia(fixture: FixtureRepositorios, zonaId: String = "zona-1") {
        fixture.catalogosLocal.reemplazarUnidades(
            listOf(
                Unidad(
                    id = "unidad-1",
                    placa = "ABC-123",
                    capacidadTon = null,
                    zonaId = zonaId,
                    responsableId = GestorSesionFake.USUARIO_ID,
                    responsableNombre = "Ana",
                    actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
                ),
            ),
        )
    }

    private fun crearViewModel(fixture: FixtureRepositorios) = RutaDelDiaViewModel(
        obtenerRutaDelDiaUseCase = ObtenerRutaDelDiaUseCase(fixture.catalogoRepository),
        obtenerZonaAsignadaUseCase = ObtenerZonaAsignadaUseCase(fixture.catalogoRepository, fixture.gestorSesion),
        observarProveedoresVisitadosHoyUseCase = ObservarProveedoresVisitadosHoyUseCase(fixture.registroAcopioRepository, fixture.reloj, TimeZone.UTC),
        observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
    )

    // 10: horaEstimada nula se expone sin hora -- ningun "--:--", ningun "00:00".
    @Test
    fun `horaEstimada nula se expone sin hora`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson(
                """[{"id":"r1","proveedorId":"p1","proveedorNombre":"Granja El Establo","orden":1,"horaEstimada":null},
                   |{"id":"r2","proveedorId":"p2","proveedorNombre":"Lechería Andina","orden":2,"horaEstimada":"14:30:00"}]
                """.trimMargin(),
            )
        }
        sembrarUnidadPropia(fixture)
        val viewModel = crearViewModel(fixture)

        val items = viewModel.uiState.esperarCargaCompleta().items
        assertEquals(2, items.size)
        assertNull(items.first { it.proveedorId == "p1" }.horaEstimadaTexto)
        assertEquals("14:30", items.first { it.proveedorId == "p2" }.horaEstimadaTexto)
    }

    @Test
    fun `un proveedor con registro de hoy aparece marcado como visitado`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson("""[{"id":"r1","proveedorId":"p1","proveedorNombre":"Granja El Establo","orden":1}]""")
        }
        sembrarUnidadPropia(fixture)
        fixture.registrosLocal.insertar(
            RegistroAcopio(
                uuidCliente = "uuid-1", serverId = null, usuarioId = GestorSesionFake.USUARIO_ID,
                proveedorId = "p1", unidadId = "unidad-1", fechaHora = LocalDateTime(2026, 9, 6, 9, 0, 0),
                litros = Decimal.parseString("100.00"), gpsLat = null, gpsLng = null, motivoObservacionId = null,
                litrosPorVoz = false, syncStatus = SyncStatus.PENDING, syncAttempts = 0, syncError = null,
                nextAttemptAt = null, creadoEn = LocalDateTime(2026, 9, 6, 9, 0, 0), sincronizadoEn = null,
            ),
        )

        val viewModel = crearViewModel(fixture)

        assertTrue(viewModel.uiState.esperarCargaCompleta().items.single().visitadoHoy)
    }

    @Test
    fun `sin unidad asignada la zona no se determina y no se inventa una ruta`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val viewModel = crearViewModel(fixture)

        val estado = viewModel.uiState.esperarCargaCompleta()
        assertFalse(estado.zonaDeterminada)
        assertTrue(estado.items.isEmpty())
        assertFalse(estado.cargando)
    }

    @Test
    fun `tocar una fila navega a registrar con el proveedorId`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson("""[{"id":"r1","proveedorId":"p1","proveedorNombre":"Granja El Establo","orden":1}]""")
        }
        sembrarUnidadPropia(fixture)
        val viewModel = crearViewModel(fixture)

        viewModel.effect.test {
            viewModel.onEvent(RutaDelDiaEvent.ProveedorSeleccionado("p1"))
            assertEquals(RutaDelDiaEffect.NavegarARegistrar("p1"), awaitItem())
        }
    }
}

/**
 * `cargar()` resuelve zona + ruta en `viewModelScope.launch` -- no hay garantía de que termine en el mismo
 * tick sincrónico en el que se construye el `ViewModel` bajo test (mismo motivo que
 * `DetalleVentaViewModelTest`: la llamada de red vía `MockEngine` no siempre resuelve sincrónicamente bajo
 * `UnconfinedTestDispatcher`). Se espera el primer estado con `cargando = false` en vez de leer `.value` a ciegas.
 */
private suspend fun kotlinx.coroutines.flow.StateFlow<RutaDelDiaUiState>.esperarCargaCompleta(): RutaDelDiaUiState {
    var estado = RutaDelDiaUiState()
    test {
        estado = awaitItem()
        while (estado.cargando) estado = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return estado
}
