package com.ecolacteos.acopio.presentation.calidad

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerAlertasPorZonaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerZonaAsignadaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerZonasDisponiblesUseCase
import com.ecolacteos.acopio.domain.usecase.ZonaOpcion
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests de `C-07` (`PROMPT_FASE_08E.md §8`, puntos 12-13). ONLINE-ONLY, `zonaId` obligatorio (trampa #8). */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertasAnomaliaViewModelTest {

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
        id = "prov-1", nombre = "Granja El Establo", zonaActualId = "zona-1", zonaActualNombre = "Zona Norte",
        codigoQr = null, actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
    )

    private fun crearViewModel(fixture: FixtureRepositorios) = viewModels.registrar(
        AlertasAnomaliaViewModel(
            obtenerZonasDisponiblesUseCase = ObtenerZonasDisponiblesUseCase(fixture.catalogoRepository),
            obtenerZonaAsignadaUseCase = ObtenerZonaAsignadaUseCase(fixture.catalogoRepository, fixture.gestorSesion),
            obtenerAlertasPorZonaUseCase = ObtenerAlertasPorZonaUseCase(fixture.innovacionRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
        ),
    )

    // 12: sin zona elegida no se dispara ninguna request; con zona elegida se dispara exactamente una.
    @Test
    fun `nunca llama sin zonaId -- con zona elegida dispara una sola vez`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.ALERTAS) {
                responderJson("""[{"id":"al-1","registroAcopioId":"srv-1","proveedorId":"prov-1","proveedorNombre":"Granja",
                    "tipo":"VOLUMEN_ATIPICO","severidad":"ALTA","creadoEn":"2026-09-06T09:00:00"}]""")
            } else {
                responderJson("""{"generadoEn":"2026-09-05T12:00:00Z","proveedores":[],"comunicados":[],
                    "prediccionesProveedor":[],"motivosObservacion":[],"tiposQueso":[],"unidades":[]}""")
            }
        }
        fixture.catalogosLocal.reemplazarProveedores(listOf(proveedor))
        val viewModel = crearViewModel(fixture)

        // Sin unidad_cache que asigne una zona a "usuario-1" -- no hay preseleccion, no se consulta sola.
        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.ALERTAS), "sin zona elegida no se dispara ninguna request (trampa #8)")
        assertNull(viewModel.uiState.value.zonaSeleccionada)

        val estado = viewModel.uiState.esperarConsultaCompleta {
            viewModel.onEvent(AlertasAnomaliaEvent.ZonaCambio(ZonaOpcion("zona-1", "Zona Norte")))
        }

        assertEquals(1, fixture.cuantasVecesSePidio(Endpoints.ALERTAS), "con zona elegida se dispara exactamente una")
        assertEquals(1, estado.alertas.size)
    }

    // 13: zScore nulo se omite; presente se formatea con 3 decimales (NUMERIC(6,3), trampa #6).
    @Test
    fun `zScore nulo se omite -- presente se formatea con 3 decimales`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.ALERTAS) {
                responderJson(
                    """[
                        {"id":"al-1","registroAcopioId":"srv-1","proveedorId":"prov-1","proveedorNombre":"Granja",
                         "tipo":"VOLUMEN_ATIPICO","severidad":"ALTA","creadoEn":"2026-09-06T09:00:00"},
                        {"id":"al-2","registroAcopioId":"srv-2","proveedorId":"prov-1","proveedorNombre":"Granja",
                         "tipo":"RIESGO_ADULTERACION","zScore":2.345,"severidad":"MEDIA","creadoEn":"2026-09-06T10:00:00"}
                    ]""".trimIndent(),
                )
            } else {
                responderJson("""{"generadoEn":"2026-09-05T12:00:00Z","proveedores":[],"comunicados":[],
                    "prediccionesProveedor":[],"motivosObservacion":[],"tiposQueso":[],"unidades":[]}""")
            }
        }
        fixture.catalogosLocal.reemplazarProveedores(listOf(proveedor))
        val viewModel = crearViewModel(fixture)

        val estado = viewModel.uiState.esperarConsultaCompleta {
            viewModel.onEvent(AlertasAnomaliaEvent.ZonaCambio(ZonaOpcion("zona-1", "Zona Norte")))
        }

        val alertas = estado.alertas
        assertNull(alertas.single { it.tipoTexto == "VOLUMEN_ATIPICO" }.zScoreTexto, "nulo se omite -- nunca 0.000")
        assertEquals("2.345", alertas.single { it.tipoTexto == "RIESGO_ADULTERACION" }.zScoreTexto)
    }
}

/**
 * `yaConsulto` arranca en `false` y pasa a `true` exactamente una vez, al terminar `consultar()` (exito o
 * error) -- a diferencia de `consultando`, que tambien arranca en `false`, es una senal monotonica segura
 * para esperar sin depender de si `StateFlow` conflacion se salta el estado intermedio.
 */
private suspend fun StateFlow<AlertasAnomaliaUiState>.esperarConsultaCompleta(disparar: () -> Unit): AlertasAnomaliaUiState {
    var estado = value
    test {
        disparar()
        estado = awaitItem()
        while (!estado.yaConsulto) estado = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return estado
}
