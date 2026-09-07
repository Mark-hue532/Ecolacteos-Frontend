package com.ecolacteos.acopio.presentation.produccion

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.CicloCapital
import com.ecolacteos.acopio.data.repository.NuevoRegistroAcopio
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import com.ecolacteos.acopio.domain.model.TipoQueso
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.ClasificarPadresRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearLoteProduccionUseCase
import com.ecolacteos.acopio.domain.usecase.CrearRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRegistrosDeProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.PadreRegistroAcopioElegible
import com.ecolacteos.acopio.domain.usecase.estadoSincronizacionDe
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.ResultadoCiclo
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.cuerpoSync
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PROVEEDOR_ID = "prov-1"

/** Tests de `P-03` (`PROMPT_FASE_08D.md §8`, puntos 7-13). */
class RegistrarLoteViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    private val tipoQueso = TipoQueso(
        id = "queso-1", nombre = "Queso Andino", rendimientoEsperadoPct = Decimal.parseString("12.00"),
        cicloCapital = CicloCapital.MADURACION, activo = true, actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
    )

    private fun crearViewModel(
        fixture: FixtureRepositorios,
        registroAcopioServerIds: List<String> = listOf("srv-ajeno"),
        registroAcopioUuidClientes: List<String> = emptyList(),
    ) = viewModels.registrar(
        RegistrarLoteViewModel(
            registroAcopioUuidClientes = registroAcopioUuidClientes,
            registroAcopioServerIds = registroAcopioServerIds,
            totalLitrosSeleccionadoTexto = "80.00",
            crearLoteProduccionUseCase = CrearLoteProduccionUseCase(fixture.loteProduccionRepository),
            observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
            borradorFormularioUseCase = BorradorFormularioUseCase(fixture.borradorFormularioRepository),
            reloj = fixture.reloj,
        ),
    )

    /** El padre `srv-ajeno` por defecto de [crearViewModel] necesita estar cacheado para resolver sin red. */
    private fun sembrarCacheAjena(fixture: FixtureRepositorios, id: String = "srv-ajeno") {
        fixture.cacheLocal.upsert(
            RegistroAcopioReferencia(
                id = id, uuidCliente = null, proveedorId = PROVEEDOR_ID, proveedorNombre = null,
                fechaHora = LocalDateTime(2026, 9, 4, 6, 0, 0), litros = Decimal.parseString("80.00"),
                tieneObservacion = false, origen = Origen.RESUMEN, actualizadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
            ),
        )
    }

    private fun llenarCampos(viewModel: RegistrarLoteViewModel, litros: String = "80.00", unidades: String = "40") {
        viewModel.onEvent(RegistrarLoteEvent.TipoQuesoCambio(tipoQueso))
        viewModel.onEvent(RegistrarLoteEvent.LitrosUsadosCambio(litros))
        viewModel.onEvent(RegistrarLoteEvent.UnidadesObtenidasCambio(unidades))
    }

    // 7: unidadesObtenidas = 0 es valido (@Min(0), no @Min(1)); -1 invalido.
    @Test
    fun `unidadesObtenidas 0 es valido y -1 es invalido`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.catalogosLocal.reemplazarTiposQueso(listOf(tipoQueso))
        sembrarCacheAjena(fixture)

        val conCero = crearViewModel(fixture)
        llenarCampos(conCero, unidades = "0")
        conCero.effect.test {
            conCero.onEvent(RegistrarLoteEvent.GuardarPresionado)
            awaitItem()
        }
        assertEquals(1, fixture.loteProduccionRepository.observarPendientes().first().size)

        val conNegativo = crearViewModel(fixture)
        llenarCampos(conNegativo, unidades = "-1")
        conNegativo.onEvent(RegistrarLoteEvent.GuardarPresionado)
        assertNotNull(conNegativo.uiState.value.errorUnidadesObtenidas)
        assertEquals(1, fixture.loteProduccionRepository.observarPendientes().first().size, "no se agrego un segundo lote")
    }

    // 8: litrosUsados = 0 es valido (inclusive); negativo invalido; 10 digitos enteros invalido.
    @Test
    fun `litrosUsados 0 es valido -- negativo invalido -- y 10 digitos enteros invalido`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.catalogosLocal.reemplazarTiposQueso(listOf(tipoQueso))
        sembrarCacheAjena(fixture)

        val conCero = crearViewModel(fixture)
        llenarCampos(conCero, litros = "0")
        conCero.effect.test {
            conCero.onEvent(RegistrarLoteEvent.GuardarPresionado)
            awaitItem()
        }
        assertEquals(1, fixture.loteProduccionRepository.observarPendientes().first().size)

        val conNegativo = crearViewModel(fixture)
        llenarCampos(conNegativo, litros = "-5.00")
        conNegativo.onEvent(RegistrarLoteEvent.GuardarPresionado)
        assertNotNull(conNegativo.uiState.value.errorLitrosUsados)

        val con10Digitos = crearViewModel(fixture)
        llenarCampos(con10Digitos, litros = "1234567890.00")
        con10Digitos.onEvent(RegistrarLoteEvent.GuardarPresionado)
        assertNotNull(con10Digitos.uiState.value.errorLitrosUsados)
        assertEquals(1, fixture.loteProduccionRepository.observarPendientes().first().size, "ninguno de los invalidos se guardo")
    }

    // 9: fecha futura es invalida.
    @Test
    fun `fecha futura es invalida y bloquea el guardado`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.catalogosLocal.reemplazarTiposQueso(listOf(tipoQueso))
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel)

        val hoy = viewModel.uiState.value.fecha!!
        val futura = hoy.plus(DatePeriod(days = 1))
        viewModel.onEvent(RegistrarLoteEvent.FechaCambio(futura))

        assertNotNull(viewModel.uiState.value.errorFecha)
        assertFalse(viewModel.uiState.value.puedeGuardar)
    }

    // 10: P-03 no expone un rendimientoPct previsto -- ningun campo del UiState de captura lo contiene.
    @Test
    fun `P-03 no expone un campo rendimientoPct en su UiState`() {
        val estado = RegistrarLoteUiState()
        assertFalse(estado.toString().contains("rendimiento", ignoreCase = true), "lo calcula el servidor -- no hay un campo previsto en P-03")
    }

    // 11: el borrador sobrevive a la recreacion, se borra al guardar, no cuenta como pendiente.
    @Test
    fun `el borrador de P-03 sobrevive a la recreacion y se borra al guardar`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.catalogosLocal.reemplazarTiposQueso(listOf(tipoQueso))
        sembrarCacheAjena(fixture)
        val primero = crearViewModel(fixture)
        llenarCampos(primero, litros = "65.00", unidades = "30")

        fixture.borradorFormularioRepository.guardar(
            "registrar_lote_produccion",
            """{"tipoQuesoId":"queso-1","litrosUsadosTexto":"65.00","unidadesObtenidasTexto":"30"}""",
        )

        val segundo = crearViewModel(fixture)
        assertTrue(segundo.uiState.value.hayBorradorParaRetomar)
        assertEquals(0, fixture.loteProduccionRepository.observarPendientes().first().size, "el borrador no cuenta como pendiente")

        segundo.onEvent(RegistrarLoteEvent.RetomarBorradorPresionado)
        assertEquals("65.00", segundo.uiState.value.litrosUsadosTexto)
        assertEquals("30", segundo.uiState.value.unidadesObtenidasTexto)
        assertFalse(segundo.uiState.value.hayBorradorParaRetomar)

        segundo.effect.test {
            segundo.onEvent(RegistrarLoteEvent.GuardarPresionado)
            awaitItem()
        }
        assertNull(fixture.borradorFormularioRepository.obtener("registrar_lote_produccion"))
    }

    /**
     * 12 y 13: el test que justifica la sub-fase, mismas dos trampas que `8C` (§7 del prompt). Padre e
     * hijo en el MISMO dispositivo -- el padre sincroniza SIN `server_id` (`DATA-014`), el lote queda
     * `EsperandoDependencia` con motivo no nulo (12). Tras refrescar el historial del proveedor, la misma
     * entrega aparece como ajena resuelta y un lote nuevo sobre ella nace sin pasar por
     * `PENDING_DEPENDENCY` (13). Ground truth -- se lee el repositorio real, nunca `uiState.value` justo
     * tras el constructor ni un `while` sobre un flujo async encadenado.
     */
    @Test
    fun `DATA-014 de punta a punta -- EsperandoDependencia tras sync y resuelto tras refrescar el historial`() = runTest {
        lateinit var uuidPadre: String
        val fixture = FixtureRepositorios { request ->
            when (request.url.encodedPath) {
                Endpoints.SYNC_REGISTROS_ACOPIO -> responderJson(cuerpoSync(confirmados = listOf(uuidPadre)))
                Endpoints.registrosAcopioPorProveedor(PROVEEDOR_ID) ->
                    responderJson("""[{"id":"srv-real-1","fechaHora":"2026-09-05T06:00:00","litros":120.50,"tieneObservacion":false}]""")
                else -> responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.catalogosLocal.reemplazarTiposQueso(listOf(tipoQueso))
        val crearRegistro = CrearRegistroAcopioUseCase(fixture.registroAcopioRepository)

        uuidPadre = crearRegistro(
            NuevoRegistroAcopio(
                proveedorId = PROVEEDOR_ID, unidadId = "unidad-1", fechaHora = LocalDateTime(2026, 9, 5, 6, 0, 0),
                litros = Decimal.parseString("120.50"), gpsLat = null, gpsLng = null, motivoObservacionId = null, litrosPorVoz = false,
            ),
        )

        // P-03 con el padre PROPIO todavia sin sincronizar (referenciado por su uuidCliente, tal como lo
        // deja P-02 para el caso 3).
        val loteViewModel1 = crearViewModel(fixture, registroAcopioServerIds = emptyList(), registroAcopioUuidClientes = listOf(uuidPadre))
        llenarCampos(loteViewModel1)
        loteViewModel1.effect.test {
            loteViewModel1.onEvent(RegistrarLoteEvent.GuardarPresionado)
            assertEquals(RegistrarLoteEffect.GuardadoConExito, awaitItem())
        }

        val antes = fixture.loteProduccionRepository.observarPendientes().first().single()
        assertEquals(com.ecolacteos.acopio.domain.model.SyncStatus.PENDING_DEPENDENCY, antes.syncStatus)

        // --- 12: corre el ciclo de sync completo -- el padre sincroniza SIN server_id (DATA-014). ---
        val resultadoCiclo = fixture.syncEngine.ejecutarCiclo()
        assertIs<ResultadoCiclo.Completado>(resultadoCiclo)

        val lote1 = fixture.loteProduccionRepository.observarPendientes().first().single()
        val estadoLote1 = estadoSincronizacionDe(lote1.syncStatus, lote1.syncError, lote1.nextAttemptAt)
        val esperando = assertIs<EstadoSincronizacion.EsperandoDependencia>(estadoLote1, "nunca Pendiente generico")
        assertNotNull(esperando.motivoConocido)
        assertTrue(esperando.motivoConocido.contains("DATA-014"))

        // --- 13: refresca el historial del proveedor -- la entrega aparece como ajena resuelta. ---
        val obtenerRegistros = ObtenerRegistrosDeProveedorUseCase(fixture.registroAcopioRepository)
        obtenerRegistros(PROVEEDOR_ID)

        val clasificador = ClasificarPadresRegistroAcopioUseCase(fixture.registroAcopioRepository)
        val elegibles = clasificador(PROVEEDOR_ID).first()
        assertTrue(elegibles.filterIsInstance<PadreRegistroAcopioElegible.AjenoDisponible>().any { it.referencia.id == "srv-real-1" })

        // Un lote NUEVO sobre esa misma referencia nace resuelto, nunca PENDING_DEPENDENCY.
        val loteViewModel2 = crearViewModel(fixture, registroAcopioServerIds = listOf("srv-real-1"), registroAcopioUuidClientes = emptyList())
        llenarCampos(loteViewModel2, litros = "50.00", unidades = "20")
        loteViewModel2.effect.test {
            loteViewModel2.onEvent(RegistrarLoteEvent.GuardarPresionado)
            assertEquals(RegistrarLoteEffect.GuardadoConExito, awaitItem())
        }

        val lote2 = fixture.loteProduccionRepository.observarPendientes().first().single { it.litrosUsados == Decimal.parseString("50.00") }
        assertEquals(com.ecolacteos.acopio.domain.model.SyncStatus.PENDING, lote2.syncStatus, "resuelto de una -- nunca PENDING_DEPENDENCY")
    }
}
