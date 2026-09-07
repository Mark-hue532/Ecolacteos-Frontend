package com.ecolacteos.acopio.presentation.acopio

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.domain.model.MotivoObservacion
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.model.Unidad
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarHistorialProveedorUseCase
import com.ecolacteos.acopio.plataforma.EstadoPermiso
import com.ecolacteos.acopio.plataforma.GestorPermisosFake
import com.ecolacteos.acopio.plataforma.Permiso
import com.ecolacteos.acopio.plataforma.ProveedorUbicacionFake
import com.ecolacteos.acopio.plataforma.ResultadoUbicacion
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private val ZONA = TimeZone.UTC
private const val PROVEEDOR_ID = "p1"
private const val UNIDAD_ID = "unidad-1"

/** Tests de `A-04` (`PROMPT_FASE_08A.md §5`, puntos 1-7 y 13 parcial). */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrarAcopioViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sembrarCatalogo(fixture: FixtureRepositorios) {
        fixture.catalogosLocal.reemplazarProveedores(
            listOf(
                Proveedor(
                    id = PROVEEDOR_ID, nombre = "Granja El Establo", zonaActualId = null, zonaActualNombre = null,
                    codigoQr = null, actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
                ),
            ),
        )
        fixture.catalogosLocal.reemplazarUnidades(
            listOf(
                Unidad(
                    id = UNIDAD_ID, placa = "ABC-123", capacidadTon = null, zonaId = null,
                    responsableId = "otro", responsableNombre = "Otro", actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
                ),
            ),
        )
    }

    /**
     * Por defecto el GPS resuelve con un fix de una vez -- así ningún test que no le importa el GPS tiene
     * que lidiar con el `Effect.AvisoGpsNoDisponible` que se dispara (una sola vez, durante `init`) apenas
     * el resultado no es `Obtenido`. Los tests que sí prueban el camino sin GPS lo piden explícito y lo
     * consumen del canal de efectos antes de `GuardadoConExito` -- ver "guarda sin GPS".
     */
    private fun crearViewModel(
        fixture: FixtureRepositorios,
        permisos: GestorPermisosFake = GestorPermisosFake(Permiso.UBICACION),
        ubicacion: ProveedorUbicacionFake = ProveedorUbicacionFake(
            ProveedorUbicacionFake.EstrategiaFake.Inmediato(
                ResultadoUbicacion.Obtenida(Decimal.parseString("-12.045678"), Decimal.parseString("-77.030348")),
            ),
        ),
        proveedorId: String = PROVEEDOR_ID,
    ) = RegistrarAcopioViewModel(
        proveedorId = proveedorId,
        crearRegistroAcopioUseCase = CrearRegistroAcopioUseCase(fixture.registroAcopioRepository),
        observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
        observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
        borradorFormularioUseCase = BorradorFormularioUseCase(fixture.borradorFormularioRepository),
        gestorPermisos = permisos,
        proveedorUbicacion = ubicacion,
        reloj = fixture.reloj,
        zona = ZONA,
    )

    private fun llenarCampos(viewModel: RegistrarAcopioViewModel, litros: String = "120.50") {
        viewModel.onEvent(RegistrarAcopioEvent.UnidadCambio(Unidad(UNIDAD_ID, "ABC-123", null, null, "otro", "Otro", LocalDateTime(2026, 9, 6, 8, 0, 0))))
        viewModel.onEvent(RegistrarAcopioEvent.LitrosCambio(litros))
    }

    // 1: guarda sin GPS -- gpsLat/gpsLng nulos, nunca "0".
    @Test
    fun `guarda sin GPS -- gpsLat y gpsLng quedan nulos -- nunca 0`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        val ubicacion = ProveedorUbicacionFake(ProveedorUbicacionFake.EstrategiaFake.Inmediato(ResultadoUbicacion.NoDisponible))
        val viewModel = crearViewModel(fixture, ubicacion = ubicacion)
        llenarCampos(viewModel)

        viewModel.effect.test {
            // GPS ya resolvió a NoDisponible durante init -- el aviso queda bufferizado en el canal
            // esperando a este primer colector (§5 regla 3: "una sola vez").
            assertEquals(RegistrarAcopioEffect.AvisoGpsNoDisponible, awaitItem())
            viewModel.onEvent(RegistrarAcopioEvent.GuardarPresionado)
            assertEquals(RegistrarAcopioEffect.GuardadoConExito, awaitItem())
        }

        val creado = fixture.registroAcopioRepository.observarPendientes().first().single()
        assertNull(creado.gpsLat)
        assertNull(creado.gpsLng)
    }

    // 2: no espera al GPS -- puedeGuardar nunca depende de gps, ni siquiera mientras sigue "Buscando".
    // Test de estado puro a propósito: un fake que "nunca responde" (awaitCancellation) deja una
    // corrutina de viewModelScope colgada para siempre, y como viewModelScope no es hijo del TestScope de
    // runTest, esa corrutina sobrevive al test y contamina la siguiente -- se vio romper otras clases en
    // la misma corrida. El caso "a los 15 segundos..." de abajo ya cubre el timeout real con un dispatcher
    // aislado; acá alcanza con el estado, sin pasar por ninguna corrutina.
    @Test
    fun `puedeGuardar nunca depende del estado de gps`() {
        val base = RegistrarAcopioUiState(
            proveedorId = PROVEEDOR_ID,
            proveedorEnCache = true,
            unidadSeleccionada = Unidad(UNIDAD_ID, "ABC-123", null, null, "otro", "Otro", LocalDateTime(2026, 9, 6, 8, 0, 0)),
            litrosTexto = "120.50",
        )
        assertTrue(base.copy(gps = EstadoGps.Buscando).puedeGuardar)
        assertTrue(base.copy(gps = EstadoGps.NoDisponible).puedeGuardar)
        assertTrue(base.copy(gps = EstadoGps.SinPermiso).puedeGuardar)
    }

    // 2b: a los 15s el estado de GPS pasa a NoDisponible si nadie respondió.
    //
    // No usa runTest: viewModelScope no es hijo del TestScope de runTest (vive mientras dure el
    // ViewModel), así que compartir el testScheduler implícito de runTest arriesga contaminar el
    // Dispatchers.Main global una vez ese scope termina -- se vio romper otras clases de test en la misma
    // corrida. Acá se crea un StandardTestDispatcher propio, se avanza a mano, y se libera con certeza.
    @Test
    fun `a los 15 segundos sin respuesta el gps pasa a NoDisponible`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
            sembrarCatalogo(fixture)
            val ubicacion = ProveedorUbicacionFake(); ubicacion.noResponderNunca()
            val viewModel = crearViewModel(fixture, ubicacion = ubicacion)
            dispatcher.scheduler.runCurrent()

            assertEquals(EstadoGps.Buscando, viewModel.uiState.value.gps)
            dispatcher.scheduler.advanceTimeBy(15_001)
            dispatcher.scheduler.runCurrent()
            assertEquals(EstadoGps.NoDisponible, viewModel.uiState.value.gps)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // 3: validación de litros -- negativo invalido, 0 valido (borde inclusive), 7 digitos enteros invalido.
    @Test
    fun `litros negativo es invalido`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel, litros = "-5.00")

        viewModel.onEvent(RegistrarAcopioEvent.GuardarPresionado)

        assertNotNull(viewModel.uiState.value.errorLitros)
        assertTrue(fixture.registroAcopioRepository.observarPendientes().first().isEmpty())
    }

    @Test
    fun `litros 0 es el borde valido -- DecimalMin es inclusive`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel, litros = "0")

        viewModel.effect.test {
            viewModel.onEvent(RegistrarAcopioEvent.GuardarPresionado)
            awaitItem()
        }
        assertEquals(1, fixture.registroAcopioRepository.observarPendientes().first().size)
    }

    @Test
    fun `7 digitos enteros es invalido`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel, litros = "1234567.00")

        viewModel.onEvent(RegistrarAcopioEvent.GuardarPresionado)

        assertNotNull(viewModel.uiState.value.errorLitros)
        assertTrue(fixture.registroAcopioRepository.observarPendientes().first().isEmpty())
    }

    // 4: fechaHora futura invalida; mas de 24h en el pasado produce aviso, no bloqueo.
    @Test
    fun `fechaHora futura es invalida y bloquea el guardado`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel)

        val futura = ahoraComoFechaHora(fixture.reloj, ZONA).plusHoras(2)
        viewModel.onEvent(RegistrarAcopioEvent.FechaHoraCambio(futura))

        assertNotNull(viewModel.uiState.value.errorFecha)
        assertFalse(viewModel.uiState.value.puedeGuardar)
    }

    @Test
    fun `fechaHora de hace mas de 24 horas produce aviso pero no bloquea`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel)

        val hace30h = ahoraComoFechaHora(fixture.reloj, ZONA).plusHoras(-30)
        viewModel.onEvent(RegistrarAcopioEvent.FechaHoraCambio(hace30h))

        assertNull(viewModel.uiState.value.errorFecha)
        assertNotNull(viewModel.uiState.value.avisoFechaPasada)
        assertTrue(viewModel.uiState.value.puedeGuardar)
    }

    // 5: sin motivo, motivoObservacionId queda nulo; litrosPorVoz nulo llega como false, nunca null.
    @Test
    fun `sin motivo de observacion el registro se crea con motivoObservacionId nulo y litrosPorVoz false`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel)

        viewModel.effect.test {
            viewModel.onEvent(RegistrarAcopioEvent.GuardarPresionado)
            awaitItem()
        }

        val creado = fixture.registroAcopioRepository.observarPendientes().first().single()
        assertNull(creado.motivoObservacionId)
        assertEquals(false, creado.litrosPorVoz)
    }

    @Test
    fun `con motivo seleccionado se persiste su id`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        fixture.catalogosLocal.reemplazarMotivosObservacion(
            listOf(MotivoObservacion(id = "m1", descripcion = "Leche aguada", actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0))),
        )
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel)
        viewModel.onEvent(RegistrarAcopioEvent.MotivoCambio(MotivoObservacion("m1", "Leche aguada", LocalDateTime(2026, 9, 6, 8, 0, 0))))

        viewModel.effect.test {
            viewModel.onEvent(RegistrarAcopioEvent.GuardarPresionado)
            awaitItem()
        }

        assertEquals("m1", fixture.registroAcopioRepository.observarPendientes().first().single().motivoObservacionId)
    }

    // 6: el borrador se persiste, sobrevive a la recreacion, se borra al guardar, no cuenta como pendiente.
    @Test
    fun `el borrador de A-04 sobrevive a la recreacion del ViewModel y se borra al guardar`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        val primero = crearViewModel(fixture)
        llenarCampos(primero, litros = "75.50")

        // Simula el debounce de ~500ms guardando el borrador directo -- mismo criterio que RegistrarVentaViewModelTest.
        fixture.borradorFormularioRepository.guardar(
            "registrar_acopio",
            """{"unidadId":"$UNIDAD_ID","litrosTexto":"75.50","motivoObservacionId":null}""",
        )

        val segundo = crearViewModel(fixture)
        assertTrue(segundo.uiState.value.hayBorradorParaRetomar)
        assertEquals(0, fixture.registroAcopioRepository.observarPendientes().first().size) // el borrador no cuenta

        segundo.onEvent(RegistrarAcopioEvent.RetomarBorradorPresionado)
        assertEquals("75.50", segundo.uiState.value.litrosTexto)
        assertFalse(segundo.uiState.value.hayBorradorParaRetomar)

        segundo.effect.test {
            segundo.onEvent(RegistrarAcopioEvent.GuardarPresionado)
            awaitItem()
        }
        assertNull(fixture.borradorFormularioRepository.obtener("registrar_acopio"))
    }

    // 7: aparicion optimista -- tras guardar, la entrega llega por el Flow de A-05 sin consultar a mano.
    @Test
    fun `tras guardar la entrega aparece de inmediato en el historial del proveedor`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel)

        val historial = ObservarHistorialProveedorUseCase(fixture.registroAcopioRepository)
        assertTrue(historial(PROVEEDOR_ID).first().isEmpty())

        viewModel.effect.test {
            viewModel.onEvent(RegistrarAcopioEvent.GuardarPresionado)
            awaitItem()
        }

        assertEquals(1, historial(PROVEEDOR_ID).first().size)
    }

    // 13 (parcial): permiso de ubicacion denegado no impide guardar.
    @Test
    fun `permiso de ubicacion denegado no impide guardar`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        sembrarCatalogo(fixture)
        val permisos = GestorPermisosFake() // sin UBICACION concedida
        val viewModel = crearViewModel(fixture, permisos = permisos)
        llenarCampos(viewModel)

        viewModel.effect.test {
            assertEquals(RegistrarAcopioEffect.SolicitarPermisoUbicacion, awaitItem())
            viewModel.onEvent(RegistrarAcopioEvent.PermisoUbicacionResuelto(EstadoPermiso.DENEGADO))
            assertEquals(RegistrarAcopioEffect.AvisoGpsNoDisponible, awaitItem())

            viewModel.onEvent(RegistrarAcopioEvent.GuardarPresionado)
            assertEquals(RegistrarAcopioEffect.GuardadoConExito, awaitItem())
        }
        assertEquals(EstadoGps.SinPermiso, viewModel.uiState.value.gps)
    }

    // Decisión #3 del checkpoint: el proveedor precargado ya no está en cache -- bloquea guardar con mensaje.
    @Test
    fun `proveedor ausente del cache bloquea el guardado con un mensaje explicito`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.catalogosLocal.reemplazarUnidades(
            listOf(Unidad(UNIDAD_ID, "ABC-123", null, null, "otro", "Otro", LocalDateTime(2026, 9, 6, 8, 0, 0))),
        )
        // A propósito: proveedor_cache vacío -- simula que se limpió entre A-02/A-03 y A-04.
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel)

        assertFalse(viewModel.uiState.value.proveedorEnCache)
        assertNotNull(viewModel.uiState.value.errorGeneral)
        assertFalse(viewModel.uiState.value.puedeGuardar)
    }
}

private fun LocalDateTime.plusHoras(horas: Int): LocalDateTime =
    (this.toInstant(ZONA) + horas.hours).toLocalDateTime(ZONA)
