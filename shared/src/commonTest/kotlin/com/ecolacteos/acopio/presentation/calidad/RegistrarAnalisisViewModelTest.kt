package com.ecolacteos.acopio.presentation.calidad

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.repository.NuevoRegistroAcopio
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.ClasificarPadresRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearAnalisisCalidadUseCase
import com.ecolacteos.acopio.domain.usecase.CrearRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRegistrosDeProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.PadreRegistroAcopioElegible
import com.ecolacteos.acopio.domain.usecase.estadoSincronizacionDe
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.synchronization.ResultadoCiclo
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.cuerpoSync
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
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

/** Tests de `C-03` (`PROMPT_FASE_08C.md §7`, puntos 6-12). */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrarAnalisisViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(
        fixture: FixtureRepositorios,
        registroAcopioUuidCliente: String? = null,
        registroAcopioServerId: String? = null,
    ) = RegistrarAnalisisViewModel(
        registroAcopioUuidCliente = registroAcopioUuidCliente,
        registroAcopioServerId = registroAcopioServerId,
        crearAnalisisCalidadUseCase = CrearAnalisisCalidadUseCase(fixture.analisisCalidadRepository),
        observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
        borradorFormularioUseCase = BorradorFormularioUseCase(fixture.borradorFormularioRepository),
    )

    private fun sembrarCacheAjena(fixture: FixtureRepositorios, id: String = "srv-ajeno") {
        fixture.cacheLocal.upsert(
            RegistroAcopioReferencia(
                id = id,
                uuidCliente = null,
                proveedorId = PROVEEDOR_ID,
                proveedorNombre = null,
                fechaHora = LocalDateTime(2026, 9, 4, 6, 0, 0),
                litros = Decimal.parseString("80.00"),
                tieneObservacion = false,
                origen = Origen.RESUMEN,
                actualizadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
            ),
        )
    }

    // 6: los 6 parametros opcionales vacios se guardan como null, nunca 0 -- folioMuestra presente.
    @Test
    fun `guarda con los 6 opcionales vacios como null y folioMuestra presente`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        sembrarCacheAjena(fixture)
        val viewModel = crearViewModel(fixture, registroAcopioServerId = "srv-ajeno")
        viewModel.onEvent(RegistrarAnalisisEvent.FolioCambio("F-001"))

        viewModel.effect.test {
            viewModel.onEvent(RegistrarAnalisisEvent.GuardarPresionado)
            assertEquals(RegistrarAnalisisEffect.GuardadoConExito, awaitItem())
        }

        val fila = fixture.analisisCalidadRepository.observarPendientes().first().single()
        assertEquals("F-001", fila.folioMuestra)
        assertNull(fila.agua)
        assertNull(fila.proteina)
        assertNull(fila.lactosa)
        assertNull(fila.densidad)
        assertNull(fila.temperatura)
        assertNull(fila.ph)
    }

    // 7: folioMuestra -- vacio invalido, 40 caracteres valido, 41 invalido.
    @Test
    fun `folioMuestra vacio es invalido -- 40 caracteres es valido -- 41 es invalido`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        sembrarCacheAjena(fixture)

        val vacio = crearViewModel(fixture, registroAcopioServerId = "srv-ajeno")
        vacio.onEvent(RegistrarAnalisisEvent.GuardarPresionado)
        assertNotNull(vacio.uiState.value.errorFolio, "vacio debe fallar la validacion")
        assertEquals(0, fixture.analisisCalidadRepository.observarPendientes().first().size)

        val folio40 = "F".repeat(40)
        val con40 = crearViewModel(fixture, registroAcopioServerId = "srv-ajeno")
        con40.onEvent(RegistrarAnalisisEvent.FolioCambio(folio40))
        con40.effect.test {
            con40.onEvent(RegistrarAnalisisEvent.GuardarPresionado)
            awaitItem()
        }
        assertEquals(1, fixture.analisisCalidadRepository.observarPendientes().first().size, "40 caracteres es valido")

        val folio41 = "F".repeat(41)
        val con41 = crearViewModel(fixture, registroAcopioServerId = "srv-ajeno")
        con41.onEvent(RegistrarAnalisisEvent.FolioCambio(folio41))
        con41.onEvent(RegistrarAnalisisEvent.GuardarPresionado)
        assertNotNull(con41.uiState.value.errorFolio, "41 caracteres debe fallar")
        assertEquals(1, fixture.analisisCalidadRepository.observarPendientes().first().size, "no se agrego una segunda fila")
    }

    // 8: aguaAnadida sin tocar el switch se escribe false, nunca null.
    @Test
    fun `aguaAnadida sin tocar el switch se escribe false`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        sembrarCacheAjena(fixture)
        val viewModel = crearViewModel(fixture, registroAcopioServerId = "srv-ajeno")
        viewModel.onEvent(RegistrarAnalisisEvent.FolioCambio("F-001"))

        viewModel.effect.test {
            viewModel.onEvent(RegistrarAnalisisEvent.GuardarPresionado)
            awaitItem()
        }

        assertFalse(fixture.analisisCalidadRepository.observarPendientes().first().single().aguaAnadida)
    }

    // 9: C-03 no expone un resultado previsto -- ningun campo del UiState de captura lo contiene.
    @Test
    fun `C-03 no expone un campo resultado en su UiState`() {
        val estado = RegistrarAnalisisUiState()
        assertFalse(
            estado.toString().contains("resultado", ignoreCase = true),
            "el resultado lo calcula el servidor -- no debe existir un campo previsto en C-03",
        )
    }

    // 10: el borrador sobrevive a la recreacion del ViewModel, se borra al guardar, y no cuenta como pendiente.
    @Test
    fun `el borrador de C-03 sobrevive a la recreacion y se borra al guardar`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        sembrarCacheAjena(fixture)
        val primero = crearViewModel(fixture, registroAcopioServerId = "srv-ajeno")
        primero.onEvent(RegistrarAnalisisEvent.FolioCambio("F-borrador"))
        primero.onEvent(RegistrarAnalisisEvent.AguaCambio("3.20"))

        // Simula el debounce de ~500ms guardando el borrador directo (mismo criterio que RegistrarAcopioViewModelTest).
        fixture.borradorFormularioRepository.guardar(
            "registrar_analisis_calidad",
            """{"folioMuestra":"F-borrador","agua":"3.20","proteina":"","lactosa":"","densidad":"","temperatura":"","ph":"","aguaAnadida":false}""",
        )

        val segundo = crearViewModel(fixture, registroAcopioServerId = "srv-ajeno")
        assertTrue(segundo.uiState.value.hayBorradorParaRetomar)
        assertEquals(0, fixture.analisisCalidadRepository.observarPendientes().first().size, "el borrador no cuenta como pendiente")

        segundo.onEvent(RegistrarAnalisisEvent.RetomarBorradorPresionado)
        assertEquals("F-borrador", segundo.uiState.value.folioMuestra)
        assertEquals("3.20", segundo.uiState.value.agua)
        assertFalse(segundo.uiState.value.hayBorradorParaRetomar)

        segundo.effect.test {
            segundo.onEvent(RegistrarAnalisisEvent.GuardarPresionado)
            awaitItem()
        }
        assertNull(fixture.borradorFormularioRepository.obtener("registrar_analisis_calidad"))
    }

    /**
     * 11 y 12: el test que justifica la sub-fase (`§0`/`§3` del prompt). Padre e hijo en el MISMO
     * dispositivo -- tras el ciclo de sync el padre queda `Sincronizado` **sin** `server_id` (`DATA-014`),
     * así que el hijo se queda `EsperandoDependencia` con [EstadoSincronizacion.EsperandoDependencia.motivoConocido]
     * no nulo -- nunca `Pendiente` genérico, nunca resuelto solo (11). Recién cuando se refresca el
     * historial del proveedor (`§3.1.2`) la misma entrega aparece como ajena resuelta y un análisis nuevo
     * sobre ella nace sin pasar por `PENDING_DEPENDENCY` (12).
     *
     * Ground truth (recuadro de `§7`): se lee el estado real en SQLite/repositorio después de cada paso, no
     * `uiState.value` justo después del constructor ni un `while` sobre un flujo async encadenado.
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
        val crearRegistro = CrearRegistroAcopioUseCase(fixture.registroAcopioRepository)

        uuidPadre = crearRegistro(
            NuevoRegistroAcopio(
                proveedorId = PROVEEDOR_ID,
                unidadId = "unidad-1",
                fechaHora = LocalDateTime(2026, 9, 5, 6, 0, 0),
                litros = Decimal.parseString("120.50"),
                gpsLat = null,
                gpsLng = null,
                motivoObservacionId = null,
                litrosPorVoz = false,
            ),
        )

        // C-03 con el padre PROPIO todavía sin sincronizar (referenciado por su uuidCliente, tal como lo
        // deja C-02 para el caso 3 -- `ClasificarPadresRegistroAcopioUseCase.aReferenciaPadre`).
        val viewModelHijo1 = crearViewModel(fixture, registroAcopioUuidCliente = uuidPadre)
        viewModelHijo1.onEvent(RegistrarAnalisisEvent.FolioCambio("F-hijo-1"))
        viewModelHijo1.effect.test {
            viewModelHijo1.onEvent(RegistrarAnalisisEvent.GuardarPresionado)
            assertEquals(RegistrarAnalisisEffect.GuardadoConExito, awaitItem())
        }

        // Antes de cualquier ciclo: retenido a proposito, ver ResolucionDePadreTest de Fase 6.
        val antes = fixture.analisisCalidadRepository.observarPendientes().first().single { it.folioMuestra == "F-hijo-1" }
        assertEquals(SyncStatus.PENDING_DEPENDENCY, antes.syncStatus)

        // --- 11: corre el ciclo de sync completo -- el padre sincroniza SIN server_id (DATA-014). ---
        val resultadoCiclo = fixture.syncEngine.ejecutarCiclo()
        assertIs<ResultadoCiclo.Completado>(resultadoCiclo)

        val hijo1 = fixture.analisisCalidadRepository.observarPendientes().first().single { it.folioMuestra == "F-hijo-1" }
        val estadoHijo1 = com.ecolacteos.acopio.domain.usecase.estadoSincronizacionDe(hijo1.syncStatus, hijo1.syncError, hijo1.nextAttemptAt)
        val esperando = assertIs<EstadoSincronizacion.EsperandoDependencia>(estadoHijo1, "nunca Pendiente generico -- §7")
        assertNotNull(esperando.motivoConocido)
        assertTrue(esperando.motivoConocido.contains("DATA-014"))

        // --- 12: refresca el historial del proveedor -- la entrega aparece como ajena resuelta (caso 1). ---
        val obtenerRegistros = ObtenerRegistrosDeProveedorUseCase(fixture.registroAcopioRepository)
        obtenerRegistros(PROVEEDOR_ID)

        val clasificador = ClasificarPadresRegistroAcopioUseCase(fixture.registroAcopioRepository)
        val elegibles = clasificador(PROVEEDOR_ID).first()
        val comoAjena = elegibles.filterIsInstance<PadreRegistroAcopioElegible.AjenoDisponible>()
        assertTrue(comoAjena.any { it.referencia.id == "srv-real-1" }, "la misma entrega ahora aparece como ajena resuelta")

        // Un análisis NUEVO sobre esa referencia nace resuelto, nunca PENDING_DEPENDENCY.
        val viewModelHijo2 = crearViewModel(fixture, registroAcopioServerId = "srv-real-1")
        viewModelHijo2.onEvent(RegistrarAnalisisEvent.FolioCambio("F-hijo-2"))
        viewModelHijo2.effect.test {
            viewModelHijo2.onEvent(RegistrarAnalisisEvent.GuardarPresionado)
            assertEquals(RegistrarAnalisisEffect.GuardadoConExito, awaitItem())
        }

        val hijo2 = fixture.analisisCalidadRepository.observarPendientes().first().single { it.folioMuestra == "F-hijo-2" }
        assertEquals(SyncStatus.PENDING, hijo2.syncStatus, "resuelto de una -- nunca PENDING_DEPENDENCY")
        assertEquals("srv-real-1", hijo2.registroAcopioServerId)
    }
}
