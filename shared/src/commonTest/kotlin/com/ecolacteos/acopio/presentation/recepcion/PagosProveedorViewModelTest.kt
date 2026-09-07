package com.ecolacteos.acopio.presentation.recepcion

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObtenerPagosDeProveedorUseCase
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Test de `R-04` (`PROMPT_FASE_08E.md §8`, punto 8). ONLINE-ONLY, solo lectura. */
@OptIn(ExperimentalCoroutinesApi::class)
class PagosProveedorViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    // 8: precioLitro con 3 decimales, litrosTotales y total con 2, todos desde BigDecimal sin pasar por Double (trampa #5).
    @Test
    fun `precioLitro con 3 decimales -- litrosTotales y total con 2`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson(
                """[{"id":"pago-1","proveedorId":"prov-1","proveedorNombre":"Granja",
                    |"semanaInicio":"2026-09-01","semanaFin":"2026-09-07",
                    |"litrosTotales":700.50,"precioLitro":1.256,"total":880.23,
                    |"comprobanteGenerado":true,"registroAcopioIds":[]}]
                """.trimMargin(),
            )
        }
        val viewModel = viewModels.registrar(
            PagosProveedorViewModel(proveedorId = "prov-1", obtenerPagosDeProveedorUseCase = ObtenerPagosDeProveedorUseCase(fixture.pagoRepository)),
        )

        val estado = viewModel.uiState.esperarCargaCompleta()
        val item = estado.items.single()
        assertEquals("1.256", item.precioLitroTexto, "precioLitro tiene 3 decimales (precision=6,scale=3), no 2 como el resto")
        assertEquals("700.50", item.litrosTotalesTexto)
        assertEquals("880.23", item.totalTexto)
    }
}

private suspend fun StateFlow<PagosProveedorUiState>.esperarCargaCompleta(): PagosProveedorUiState {
    var estado = PagosProveedorUiState()
    test {
        estado = awaitItem()
        while (estado.cargando) estado = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return estado
}
