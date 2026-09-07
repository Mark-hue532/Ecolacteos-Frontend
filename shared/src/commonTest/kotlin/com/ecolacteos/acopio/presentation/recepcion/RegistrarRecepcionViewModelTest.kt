package com.ecolacteos.acopio.presentation.recepcion

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.model.Unidad
import com.ecolacteos.acopio.domain.usecase.BuscarRecepcionesUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.RegistrarRecepcionUseCase
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.ConnectivityObserverFake
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private val ZONA = TimeZone.UTC

/** Tests de `R-01`/`R-02b` (`PROMPT_FASE_08E.md §8`, puntos 1-5). ONLINE-ONLY, sin cola (`DATA-006`, trampa #1). */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrarRecepcionViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    private val unidad = Unidad(
        id = "unidad-1", placa = "ABC-123", capacidadTon = null, zonaId = "zona-1",
        responsableId = "otro-usuario", responsableNombre = "Otro", actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
    )

    private fun crearViewModel(fixture: FixtureRepositorios) = viewModels.registrar(
        RegistrarRecepcionViewModel(
            registrarRecepcionUseCase = RegistrarRecepcionUseCase(fixture.recepcionPlantaRepository),
            buscarRecepcionesUseCase = BuscarRecepcionesUseCase(fixture.recepcionPlantaRepository),
            observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
            reloj = fixture.reloj,
            zona = ZONA,
        ),
    )

    // 1: sin conexion la pantalla se bloquea -- no se dispara ninguna request y no se encola nada.
    @Test
    fun `sin conexion no dispara ninguna request al guardar`() = runTest {
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = false)) {
            responderJson(cuerpoCambiosVacio())
        }
        fixture.catalogosLocal.reemplazarUnidades(listOf(unidad))
        val viewModel = crearViewModel(fixture)

        viewModel.onEvent(RegistrarRecepcionEvent.UnidadCambio(unidad))
        viewModel.onEvent(RegistrarRecepcionEvent.LitrosCampoCambio("100.00"))
        viewModel.onEvent(RegistrarRecepcionEvent.LitrosPlantaCambio("98.00"))
        assertFalse(viewModel.uiState.value.puedeGuardar, "sin conexion el boton queda deshabilitado")

        // Igual se dispara el evento (defensa en profundidad -- no solo confiar en que la UI no deje tocar el boton).
        viewModel.onEvent(RegistrarRecepcionEvent.GuardarPresionado)

        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.RECEPCION_PLANTA), "DATA-006/trampa #1 -- sin conexion nunca se dispara ni se encola")
    }

    // 2: turno vacio se envia como null -- nunca "" ni "UNICO" puesto por el cliente (trampa #3).
    @Test
    fun `turno vacio se envia como null -- nunca UNICO puesto por el cliente`() = runTest {
        var bodyCapturado: String? = null
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = true)) { request ->
            if (request.url.encodedPath == Endpoints.RECEPCION_PLANTA) {
                bodyCapturado = (request.body as TextContent).text
                responderJson(
                    """{"id":"rec-1","fecha":"2026-09-06","turno":"UNICO","unidadId":"unidad-1",
                       |"litrosCampo":100.00,"litrosPlanta":98.00,"diferenciaPct":2.00,"estado":"OK"}
                    """.trimMargin(),
                )
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.catalogosLocal.reemplazarUnidades(listOf(unidad))
        val viewModel = crearViewModel(fixture)

        viewModel.onEvent(RegistrarRecepcionEvent.UnidadCambio(unidad))
        viewModel.onEvent(RegistrarRecepcionEvent.LitrosCampoCambio("100.00"))
        viewModel.onEvent(RegistrarRecepcionEvent.LitrosPlantaCambio("98.00"))
        // turnoTexto queda en "" -- nunca se toca.

        viewModel.effect.test {
            viewModel.onEvent(RegistrarRecepcionEvent.GuardarPresionado)
            assertEquals(RegistrarRecepcionEffect.NavegarAResultado("rec-1"), awaitItem())
        }

        // JsonConfig usa explicitNulls = false: un turno null se OMITE del body, nunca "" ni "UNICO".
        assertFalse(bodyCapturado!!.contains("\"turno\""), "el turno vacio no viaja como campo -- $bodyCapturado")
    }

    // 3: litrosCampo/litrosPlanta en 0 validos; negativos invalidos.
    @Test
    fun `litrosCampo y litrosPlanta en 0 son validos -- negativos invalidos`() = runTest {
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = true)) { request ->
            if (request.url.encodedPath == Endpoints.RECEPCION_PLANTA) {
                responderJson(
                    """{"id":"rec-cero","fecha":"2026-09-06","turno":"UNICO","unidadId":"unidad-1",
                       |"litrosCampo":0.00,"litrosPlanta":0.00,"diferenciaPct":0.00,"estado":"OK"}
                    """.trimMargin(),
                )
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.catalogosLocal.reemplazarUnidades(listOf(unidad))

        val conCero = crearViewModel(fixture)
        conCero.onEvent(RegistrarRecepcionEvent.UnidadCambio(unidad))
        conCero.onEvent(RegistrarRecepcionEvent.LitrosCampoCambio("0"))
        conCero.onEvent(RegistrarRecepcionEvent.LitrosPlantaCambio("0"))
        conCero.effect.test {
            conCero.onEvent(RegistrarRecepcionEvent.GuardarPresionado)
            assertEquals(RegistrarRecepcionEffect.NavegarAResultado("rec-cero"), awaitItem())
        }
        assertNull(conCero.uiState.value.errorLitrosCampo, "0 es un litraje valido")
        assertNull(conCero.uiState.value.errorLitrosPlanta, "0 es un litraje valido")

        val conNegativo = crearViewModel(fixture)
        conNegativo.onEvent(RegistrarRecepcionEvent.UnidadCambio(unidad))
        conNegativo.onEvent(RegistrarRecepcionEvent.LitrosCampoCambio("-1"))
        conNegativo.onEvent(RegistrarRecepcionEvent.LitrosPlantaCambio("50"))
        val antes = fixture.cuantasVecesSePidio(Endpoints.RECEPCION_PLANTA)
        conNegativo.onEvent(RegistrarRecepcionEvent.GuardarPresionado)
        assertEquals("Los litros no pueden ser negativos", conNegativo.uiState.value.errorLitrosCampo)
        assertEquals(antes, fixture.cuantasVecesSePidio(Endpoints.RECEPCION_PLANTA), "un negativo nunca llega a dispararse")
    }

    // 4: un 409 produce el conflicto con sus tres opciones, dispara UNA sola busqueda, nunca reintenta el POST.
    @Test
    fun `409 produce el conflicto -- una sola busqueda y nunca reintenta el POST`() = runTest {
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = true)) { request ->
            when {
                request.url.encodedPath == Endpoints.RECEPCION_PLANTA && request.method == HttpMethod.Post ->
                    respondError(HttpStatusCode.Conflict)
                request.url.encodedPath == Endpoints.RECEPCION_PLANTA ->
                    responderJson(
                        """[{"id":"rec-existente","fecha":"2026-09-06","turno":"UNICO","unidadId":"unidad-1",
                           |"litrosCampo":100.00,"litrosPlanta":98.00,"diferenciaPct":2.00,"estado":"OK"}]
                        """.trimMargin(),
                    )
                else -> responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.catalogosLocal.reemplazarUnidades(listOf(unidad))
        val viewModel = crearViewModel(fixture)

        viewModel.onEvent(RegistrarRecepcionEvent.UnidadCambio(unidad))
        viewModel.onEvent(RegistrarRecepcionEvent.LitrosCampoCambio("100.00"))
        viewModel.onEvent(RegistrarRecepcionEvent.LitrosPlantaCambio("98.00"))
        val estado = viewModel.uiState.esperarConflicto { viewModel.onEvent(RegistrarRecepcionEvent.GuardarPresionado) }

        val conflicto = estado.conflicto
        assertEquals("rec-existente", conflicto?.recepcionExistente?.id)
        assertEquals("UNICO", conflicto?.turnoReal)
        // 1 POST (409) + exactamente 1 GET de busqueda -- nunca un segundo intento del POST (§8, trampa #2).
        assertEquals(2, fixture.cuantasVecesSePidio(Endpoints.RECEPCION_PLANTA), "una sola busqueda, nunca reintenta el POST")

        // Las tres opciones del flujo (§9 R-02b) siguen disponibles sin volver a tocar la red.
        viewModel.onEvent(RegistrarRecepcionEvent.CambiarTurnoPresionado)
        assertNull(viewModel.uiState.value.conflicto)
    }

    // 5: el mensaje nombra el turno REAL que colisiono ("UNICO"), incluso si la busqueda posterior falla.
    @Test
    fun `nombra el turno real UNICO aunque el usuario haya dejado el campo vacio`() = runTest {
        val fixture = FixtureRepositorios(conectividad = ConnectivityObserverFake(inicial = true)) { request ->
            when {
                request.url.encodedPath == Endpoints.RECEPCION_PLANTA && request.method == HttpMethod.Post ->
                    respondError(HttpStatusCode.Conflict)
                request.url.encodedPath == Endpoints.RECEPCION_PLANTA -> respondError(HttpStatusCode.ServiceUnavailable)
                else -> responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.catalogosLocal.reemplazarUnidades(listOf(unidad))
        val viewModel = crearViewModel(fixture)

        viewModel.onEvent(RegistrarRecepcionEvent.UnidadCambio(unidad))
        viewModel.onEvent(RegistrarRecepcionEvent.LitrosCampoCambio("100.00"))
        viewModel.onEvent(RegistrarRecepcionEvent.LitrosPlantaCambio("98.00"))
        val estado = viewModel.uiState.esperarConflicto { viewModel.onEvent(RegistrarRecepcionEvent.GuardarPresionado) }

        val conflicto = estado.conflicto
        assertNull(conflicto?.recepcionExistente, "la busqueda posterior tambien fallo -- no se inventa un registro")
        assertEquals("UNICO", conflicto?.turnoReal, "el turno real resuelto server-side, nunca vacio")
    }
}

/**
 * Espera a que se resuelva el conflicto de `R-02b` -- `enviando` arranca en `false` (igual que `buscando`
 * en `BuscarAnalisisPorFolioViewModelTest`), asi que se compara contra el estado inicial en vez de contar
 * transiciones (verdad de terreno, leccion de `8B`).
 */
private suspend fun StateFlow<RegistrarRecepcionUiState>.esperarConflicto(disparar: () -> Unit): RegistrarRecepcionUiState {
    val estadoInicial = value
    var estado = estadoInicial
    test {
        disparar()
        estado = awaitItem()
        while (estado == estadoInicial || estado.enviando) estado = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return estado
}
