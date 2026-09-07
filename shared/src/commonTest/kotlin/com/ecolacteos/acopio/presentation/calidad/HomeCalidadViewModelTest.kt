package com.ecolacteos.acopio.presentation.calidad

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.domain.model.AnalisisCalidad
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.usecase.FixtureRepositorios
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarEntregasConEstadoAnalisisUseCase
import com.ecolacteos.acopio.synchronization.GestorSesionFake
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
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
import kotlin.test.assertTrue

/** Test de `C-01` (`PROMPT_FASE_08C.md §7`, punto 14). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeCalidadViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 14: entregas con y sin analisis se distinguen correctamente.
    @Test
    fun `distingue entregas con y sin analisis`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        fixture.cacheLocal.upsert(
            RegistroAcopioReferencia(
                id = "srv-analizada", uuidCliente = null, proveedorId = "prov-1", proveedorNombre = "Granja A",
                fechaHora = LocalDateTime(2026, 9, 4, 6, 0, 0), litros = Decimal.parseString("80.00"),
                tieneObservacion = false, origen = Origen.RESUMEN, actualizadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
            ),
        )
        fixture.cacheLocal.upsert(
            RegistroAcopioReferencia(
                id = "srv-sin-analizar", uuidCliente = null, proveedorId = "prov-2", proveedorNombre = "Granja B",
                fechaHora = LocalDateTime(2026, 9, 5, 6, 0, 0), litros = Decimal.parseString("60.00"),
                tieneObservacion = false, origen = Origen.RESUMEN, actualizadoEn = LocalDateTime(2026, 9, 5, 6, 0, 0),
            ),
        )
        fixture.analisisLocal.insertar(
            AnalisisCalidad(
                uuidCliente = "an-1", serverId = null, usuarioId = GestorSesionFake.USUARIO_ID,
                registroAcopioUuidCliente = null, registroAcopioServerId = "srv-analizada",
                folioMuestra = "F-1", agua = null, proteina = null, lactosa = null, densidad = null,
                temperatura = null, ph = null, aguaAnadida = false, syncStatus = SyncStatus.PENDING,
                syncAttempts = 0, syncError = null, nextAttemptAt = null,
                creadoEn = LocalDateTime(2026, 9, 5, 7, 0, 0), sincronizadoEn = null,
            ),
        )

        val viewModel = HomeCalidadViewModel(
            observarEntregasConEstadoAnalisisUseCase = ObservarEntregasConEstadoAnalisisUseCase(
                fixture.registroAcopioRepository, fixture.analisisCalidadRepository,
            ),
            observarCatalogosUseCase = ObservarCatalogosUseCase(fixture.catalogoRepository),
            observarConectividadUseCase = ObservarConectividadUseCase(fixture.conectividad),
        )

        viewModel.uiState.test {
            var estado = awaitItem()
            while (estado.cargando) estado = awaitItem()

            val analizada = estado.items.single { it.registroAcopioId == "srv-analizada" }
            val sinAnalizar = estado.items.single { it.registroAcopioId == "srv-sin-analizar" }
            assertTrue(analizada.tieneAnalisis)
            assertTrue(!sinAnalizar.tieneAnalisis)
        }
    }
}
