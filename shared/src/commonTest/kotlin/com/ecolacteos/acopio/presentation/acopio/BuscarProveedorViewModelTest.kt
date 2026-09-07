package com.ecolacteos.acopio.presentation.acopio

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.usecase.BuscarProveedorPorNombreUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertTrue

/** Tests de `A-03` (`PROMPT_FASE_08A.md §4`). */
@OptIn(ExperimentalCoroutinesApi::class)
class BuscarProveedorViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(fixture: FixtureRepositorios) = BuscarProveedorViewModel(
        buscarProveedorPorNombreUseCase = BuscarProveedorPorNombreUseCase(fixture.catalogoRepository),
        observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
    )

    @Test
    fun `catalogo vacio muestra el vacio de sin catalogo -- no sin coincidencias`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val viewModel = crearViewModel(fixture)

        val estado = viewModel.uiState.value
        assertTrue(estado.vacioSinCatalogo)
        assertTrue(!estado.vacioSinCoincidencias)
    }

    @Test
    fun `catalogo con datos pero sin coincidencias muestra ningun proveedor coincide`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.catalogosLocal.reemplazarProveedores(
            listOf(
                Proveedor(
                    id = "p1", nombre = "Granja El Establo", zonaActualId = null, zonaActualNombre = null,
                    codigoQr = null, actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
                ),
            ),
        )
        val viewModel = crearViewModel(fixture)

        viewModel.onEvent(BuscarProveedorEvent.QueryCambio("no existe"))

        val estado = viewModel.uiState.value
        assertTrue(estado.vacioSinCoincidencias)
        assertTrue(!estado.vacioSinCatalogo)
    }

    @Test
    fun `seleccionar un resultado navega a registrar`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val viewModel = crearViewModel(fixture)

        viewModel.effect.test {
            viewModel.onEvent(BuscarProveedorEvent.ProveedorSeleccionado("p1"))
            assertEquals(BuscarProveedorEffect.NavegarARegistrar("p1"), awaitItem())
        }
    }
}
