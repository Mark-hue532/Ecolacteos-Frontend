package com.ecolacteos.acopio.presentation.calidad

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObtenerScoreConfianzaUseCase
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Test de `C-08` (`PROMPT_FASE_08E.md §8`, punto 14). ONLINE-ONLY. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoreConfianzaViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    // 14: un 404 produce el estado vacio con su texto -- no un estado de error (trampa #7).
    @Test
    fun `404 produce el estado vacio -- no un error`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.NotFound) }
        val viewModel = viewModels.registrar(
            ScoreConfianzaViewModel(proveedorId = "prov-1", obtenerScoreConfianzaUseCase = ObtenerScoreConfianzaUseCase(fixture.innovacionRepository)),
        )

        val estado = viewModel.uiState.esperarCargaCompleta()
        assertFalse(estado.encontrado, "404 = sin historico -- estado vacio")
        assertNull(estado.mensajeError, "un 404 de negocio no es lo mismo que un fallo de red")
    }
}

private suspend fun StateFlow<ScoreConfianzaUiState>.esperarCargaCompleta(): ScoreConfianzaUiState {
    var estado = ScoreConfianzaUiState()
    test {
        estado = awaitItem()
        while (estado.cargando) estado = awaitItem()
        cancelAndIgnoreRemainingEvents()
    }
    return estado
}
