package com.ecolacteos.acopio.presentation.ventas

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.CicloCapital
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.domain.model.TipoQueso
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearVentaUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TIPO_QUESO_DE_PRUEBA = TipoQueso(
    id = "queso-1",
    nombre = "Queso Andino",
    rendimientoEsperadoPct = Decimal.parseString("12.00"),
    cicloCapital = CicloCapital.MADURACION,
    activo = true,
    actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
)

/** Tests de `V-02` (`PROMPT_FASE_07.md §9`, puntos 1, 2, 3, 4, 5, 8 y 12). */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrarVentaViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(fixture: FixtureRepositorios): RegistrarVentaViewModel {
        fixture.catalogosLocal.reemplazarTiposQueso(listOf(TIPO_QUESO_DE_PRUEBA))
        return RegistrarVentaViewModel(
            crearVentaUseCase = CrearVentaUseCase(fixture.ventaRepository),
            observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
            borradorFormularioUseCase = BorradorFormularioUseCase(fixture.borradorFormularioRepository),
            reloj = fixture.reloj,
            zona = TimeZone.UTC,
        )
    }

    private fun llenarCampos(viewModel: RegistrarVentaViewModel, cantidad: String = "10", precio: String = "25.50") {
        viewModel.onEvent(RegistrarVentaEvent.TipoClienteCambio(TipoClienteVenta.MAYORISTA))
        viewModel.onEvent(RegistrarVentaEvent.TipoQuesoCambio(TIPO_QUESO_DE_PRUEBA))
        viewModel.onEvent(RegistrarVentaEvent.CantidadCambio(cantidad))
        viewModel.onEvent(RegistrarVentaEvent.PrecioCambio(precio))
    }

    // 1: transición completa evento por evento hasta guardar.
    @Test
    fun `transicion completa hasta guardar refleja la venta creada`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val viewModel = crearViewModel(fixture)

        llenarCampos(viewModel)
        assertTrue(viewModel.uiState.value.puedeGuardar)

        viewModel.effect.test {
            viewModel.onEvent(RegistrarVentaEvent.GuardarPresionado)
            assertEquals(RegistrarVentaEffect.GuardadoConExito, awaitItem())
        }

        assertFalse(viewModel.uiState.value.guardando)
        val creadas = fixture.ventaRepository.observarPendientes().first()
        assertEquals(1, creadas.size)
        assertEquals(10, creadas.first().cantidad)
    }

    // 2: cantidad = 0 invalida, cantidad = 1 valida (borde exacto de @Min(1)).
    @Test
    fun `cantidad 0 es invalida y cantidad 1 es el borde valido`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel, cantidad = "0")

        viewModel.onEvent(RegistrarVentaEvent.GuardarPresionado)
        assertNotNull(viewModel.uiState.value.errorCantidad)
        assertEquals(0, fixture.ventaRepository.observarPendientes().first().size)

        viewModel.onEvent(RegistrarVentaEvent.CantidadCambio("1"))
        assertTrue(viewModel.uiState.value.puedeGuardar)
        viewModel.effect.test {
            viewModel.onEvent(RegistrarVentaEvent.GuardarPresionado)
            awaitItem()
        }
        assertNull(viewModel.uiState.value.errorCantidad)
        assertEquals(1, fixture.ventaRepository.observarPendientes().first().size)
    }

    // 3: DATA-010 -- el selector nunca ofrece UNKNOWN, y aunque llegara, el UseCase lo rechaza.
    @Test
    fun `el selector de tipoCliente nunca incluye UNKNOWN y un valor invalido no se guarda`() = runTest {
        assertFalse(OPCIONES_TIPO_CLIENTE.contains(TipoClienteVenta.UNKNOWN))
        assertEquals(
            listOf(TipoClienteVenta.MAYORISTA, TipoClienteVenta.PROVEEDOR, TipoClienteVenta.PUBLICO),
            OPCIONES_TIPO_CLIENTE,
        )

        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel)
        // Fuerza el caso "fuera del enum real" que solo puede llegar como fallback de deserialización.
        viewModel.onEvent(RegistrarVentaEvent.TipoClienteCambio(TipoClienteVenta.UNKNOWN))

        viewModel.onEvent(RegistrarVentaEvent.GuardarPresionado)

        assertEquals("Seleccioná un tipo de cliente válido", viewModel.uiState.value.errorTipoCliente)
        assertEquals(0, fixture.ventaRepository.observarPendientes().first().size)
    }

    // 4: el Effect se consume una sola vez y no se reemite al recrear el ViewModel.
    @Test
    fun `el effect de guardado no se reemite en un ViewModel nuevo`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val primero = crearViewModel(fixture)
        llenarCampos(primero)
        primero.effect.test {
            primero.onEvent(RegistrarVentaEvent.GuardarPresionado)
            assertEquals(RegistrarVentaEffect.GuardadoConExito, awaitItem())
        }

        val segundo = crearViewModel(fixture)
        segundo.effect.test {
            expectNoEvents()
        }
    }

    // 5: offline-first -- con conectividad en false, guardar igual funciona y queda pendiente.
    @Test
    fun `guardar funciona sin conexion y la venta queda pendiente`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.conectividad.emitir(false)
        val viewModel = crearViewModel(fixture)
        llenarCampos(viewModel)

        viewModel.effect.test {
            viewModel.onEvent(RegistrarVentaEvent.GuardarPresionado)
            assertEquals(RegistrarVentaEffect.GuardadoConExito, awaitItem())
        }

        val pendientes = fixture.ventaRepository.observarPendientes().first()
        assertEquals(1, pendientes.size)
        assertEquals(com.ecolacteos.acopio.domain.model.SyncStatus.PENDING, pendientes.first().syncStatus)
    }

    // 8: antes de guardar, el UiState no expone ningun total calculado -- solo un subtotal etiquetado como estimado.
    @Test
    fun `antes de guardar no hay total calculado -- solo subtotal estimado`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val viewModel = crearViewModel(fixture)

        assertNull(viewModel.uiState.value.subtotalEstimadoTexto)
        llenarCampos(viewModel, cantidad = "10", precio = "25.50")

        val subtotal = viewModel.uiState.value.subtotalEstimadoTexto
        assertNotNull(subtotal)
        assertTrue(subtotal.contains("estimado"))
        assertFalse(subtotal.contains("Total", ignoreCase = false))
        assertEquals("S/ 255.00 (estimado)", subtotal)
    }

    // 12: el borrador se persiste, sobrevive a la recreacion del ViewModel, se borra al guardar.
    @Test
    fun `el borrador sobrevive a la recreacion del ViewModel y se borra al guardar`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val primero = crearViewModel(fixture)
        llenarCampos(primero, cantidad = "7", precio = "12.00")

        // Simula el debounce de ~500ms guardando el borrador directo (evita depender de un scheduler real).
        // Formato JSON literal: `BorradorVenta` es privada a `RegistrarVentaViewModel.kt`, no exportable.
        fixture.borradorFormularioRepository.guardar(
            "registrar_venta",
            """{"tipoCliente":"MAYORISTA","tipoQuesoId":"queso-1","cantidadTexto":"7","precioUnitarioTexto":"12.00"}""",
        )

        val segundo = crearViewModel(fixture)
        assertTrue(segundo.uiState.value.hayBorradorParaRetomar)

        segundo.onEvent(RegistrarVentaEvent.RetomarBorradorPresionado)
        assertEquals("7", segundo.uiState.value.cantidadTexto)
        assertEquals("12.00", segundo.uiState.value.precioUnitarioTexto)
        assertFalse(segundo.uiState.value.hayBorradorParaRetomar)

        segundo.effect.test {
            segundo.onEvent(RegistrarVentaEvent.GuardarPresionado)
            awaitItem()
        }

        // Un borrador no es un registro pendiente -- se borra al guardar y no cuenta aparte de la venta real.
        assertNull(fixture.borradorFormularioRepository.obtener("registrar_venta"))
    }
}
