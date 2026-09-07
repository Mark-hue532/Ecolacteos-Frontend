package com.ecolacteos.acopio.presentation.produccion

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.CicloCapital
import com.ecolacteos.acopio.domain.model.LoteProduccion
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.TipoQueso
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarLotesRecientesUseCase
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.presentation.ContextoDeViewModelsDePrueba
import com.ecolacteos.acopio.synchronization.GestorSesionFake
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Test de `P-01` (`PROMPT_FASE_08D.md §8`, punto 15). */
class HomeProduccionViewModelTest {

    private val viewModels = ContextoDeViewModelsDePrueba()

    @BeforeTest
    fun setUp() {
        viewModels.iniciar()
    }

    @AfterTest
    fun tearDown() {
        viewModels.finalizar()
    }

    // 15: offline -- los lotes locales se listan con el nombre del tipo de queso resuelto desde el cache, sin rendimiento -- nunca 0.
    @Test
    fun `offline -- lista lotes locales con tipo de queso resuelto y sin rendimiento real`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.catalogosLocal.reemplazarTiposQueso(
            listOf(
                TipoQueso(
                    id = "queso-1", nombre = "Queso Andino", rendimientoEsperadoPct = Decimal.parseString("12.00"),
                    cicloCapital = CicloCapital.MADURACION, activo = true, actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
                ),
            ),
        )
        fixture.lotesLocal.insertar(
            LoteProduccion(
                uuidCliente = "lote-1", serverId = null, usuarioId = GestorSesionFake.USUARIO_ID,
                fecha = LocalDate(2026, 9, 6), tipoQuesoId = "queso-1", litrosUsados = Decimal.parseString("100.00"),
                unidadesObtenidas = 45, syncStatus = SyncStatus.PENDING, syncAttempts = 0, syncError = null,
                nextAttemptAt = null, creadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0), sincronizadoEn = null,
            ),
        )

        val viewModel = viewModels.registrar(
            HomeProduccionViewModel(
                observarLotesRecientesUseCase = ObservarLotesRecientesUseCase(
                    fixture.loteProduccionRepository,
                    ObservarCatalogosUseCase(fixture.catalogoRepository),
                ),
                observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
            ),
        )

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            val item = estado.items.single()
            assertEquals("Queso Andino", item.tipoQuesoNombreTexto)
            assertEquals("100.00", item.litrosUsadosTexto)
            assertEquals(45, item.unidadesObtenidas)
            assertEquals("12.00", item.rendimientoEsperadoTexto, "muestra el esperado, nunca un real inventado")
            assertFalse(item.rendimientoEsperadoTexto == "0.00", "nunca 0")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
