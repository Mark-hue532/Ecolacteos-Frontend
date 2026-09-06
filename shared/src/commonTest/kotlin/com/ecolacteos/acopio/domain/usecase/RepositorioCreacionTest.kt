package com.ecolacteos.acopio.domain.usecase

import app.cash.turbine.test
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.repository.NuevoRegistroAcopio
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.cuerpoSync
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests 1 y 2 de `PROMPT_FASE_06.md §9`: crear offline aparece de inmediato como `Pendiente` vía `Flow`, y
 * el mismo ítem pasa a `Sincronizado` tras un ciclo -- **sin volver a consultar manualmente**, es la gracia
 * de exponer `Flow` de punta a punta (Repository → SQLite → UseCase).
 */
class RepositorioCreacionTest {

    private fun datosRegistro() = NuevoRegistroAcopio(
        proveedorId = "prov-1",
        unidadId = "unidad-1",
        fechaHora = LocalDateTime(2026, 9, 6, 6, 0, 0),
        litros = Decimal.parseString("120.50"),
        gpsLat = Decimal.parseString("-12.045678"),
        gpsLng = Decimal.parseString("-77.030348"),
        motivoObservacionId = null,
        litrosPorVoz = false,
    )

    @Test
    fun `crear offline aparece de inmediato como Pendiente via Flow`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        val useCase = CrearRegistroAcopioUseCase(fixture.registroAcopioRepository)
        val observar = ObservarPendientesUseCase(
            fixture.registroAcopioRepository,
            fixture.analisisCalidadRepository,
            fixture.loteProduccionRepository,
            fixture.ventaRepository,
        )

        observar().test {
            assertEquals(0, awaitItem().total)

            val uuidCliente = useCase(datosRegistro())

            val resumen = awaitItem()
            assertEquals(1, resumen.registros.size)
            assertEquals(uuidCliente, resumen.registros.single().dato.uuidCliente)
            assertEquals(EstadoSincronizacionEsperado.pendiente(), resumen.registros.single().estado)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un ciclo de sync exitoso pasa el mismo item a Sincronizado sin volver a consultar`() = runTest {
        lateinit var uuidCliente: String
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson(cuerpoSync(confirmados = listOf(uuidCliente)))
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val useCase = CrearRegistroAcopioUseCase(fixture.registroAcopioRepository)

        fixture.registroAcopioRepository.observarPendientes().test {
            assertEquals(emptyList(), awaitItem())

            uuidCliente = useCase(datosRegistro())
            assertEquals(SyncStatus.PENDING, awaitItem().single().syncStatus)

            // El Repository que recibe el UseCase tiene `solicitarSyncOportunista()` neutralizado en este
            // fixture (ver `FixtureRepositorios.syncEngineParaRepositorios`) -- así el único disparo del
            // ciclo es este, explícito, y el test controla el punto exacto en el que corre.
            val resultado = fixture.syncEngine.ejecutarCiclo()
            assertIs<com.ecolacteos.acopio.synchronization.ResultadoCiclo.Completado>(resultado)

            // El ciclo escribe SYNCING antes de enviar (§6.6) y recién después SYNCED al reconciliar --
            // dos escrituras a SQLite. El `Flow` de SQLDelight puede emitir las dos por separado o, si el
            // colector no llega a leer entre una y otra, conflacionarlas en una sola (va directo a SYNCED)
            // -- cualquiera de los dos es válido, lo único que no puede pasar es quedarse sin llegar nunca.
            var estado = awaitItem().single().syncStatus
            if (estado == SyncStatus.SYNCING) {
                estado = awaitItem().single().syncStatus
            }
            assertEquals(SyncStatus.SYNCED, estado)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** Pequeño helper para no repetir la construcción de `EstadoSincronizacion.Pendiente` en cada assert. */
private object EstadoSincronizacionEsperado {
    fun pendiente() = com.ecolacteos.acopio.domain.model.EstadoSincronizacion.Pendiente
}
