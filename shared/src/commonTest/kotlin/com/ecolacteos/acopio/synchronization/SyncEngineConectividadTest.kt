package com.ecolacteos.acopio.synchronization

import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.network.Endpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Disparo por reconexión (`MOBILE_ARCHITECTURE.md §6.5`, `PROMPT_FASE_05.md §4`).
 *
 * Estos dos tests no usan el scheduler virtual de `runTest`: el ciclo se dispara desde **otra** corrutina y
 * atraviesa el cliente Ktor, que despacha por su cuenta -- `advanceUntilIdle()` volvería antes de que el
 * trabajo real termine y el test pasaría o fallaría por azar. Se espera el efecto observable con un
 * timeout real, que es determinista sin depender de los internos de Ktor.
 */
class SyncEngineConectividadTest {

    private suspend fun esperarHasta(descripcion: String, condicion: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) {
                while (!condicion()) delay(5)
            }
        }
    }

    /** Margen para detectar un disparo de más: si el motor dispara dos veces, acá ya se ve. */
    private suspend fun margenParaDisparosDeMas() {
        withContext(Dispatchers.Default) { delay(200) }
    }

    @Test
    fun `una transicion false a true dispara exactamente un ciclo`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson(cuerpoSync(confirmados = listOf("reg-1")))
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("reg-1")
        val scope = CoroutineScope(Dispatchers.Default)

        val observacion = fixture.engine.observarConectividad(scope)
        margenParaDisparosDeMas()
        assertEquals(0, fixture.rutasPedidas.size, "arrancar desconectado no dispara nada")

        fixture.conectividad.emitir(true)
        // `/sync/cambios` es el último paso del ciclo: verlo significa que el ciclo entero terminó.
        esperarHasta("el ciclo disparado por la reconexión") { fixture.cuantasVecesSePidio(Endpoints.SYNC_CAMBIOS) == 1 }

        fixture.conectividad.emitir(false)
        margenParaDisparosDeMas()

        assertEquals(1, fixture.cuantasVecesSePidio(Endpoints.SYNC_REGISTROS_ACOPIO), "ni cero ni dos ciclos")
        assertEquals(SyncStatus.SYNCED, fixture.registros.obtenerPorUuidCliente("reg-1")?.syncStatus)
        observacion.cancel()
        scope.cancel()
    }

    @Test
    fun `mantenerse conectado no vuelve a disparar -- solo la transicion cuenta`() = runTest {
        val fixture = FixtureDeSync { responderJson(cuerpoCambiosVacio()) }
        val scope = CoroutineScope(Dispatchers.Default)

        val observacion = fixture.engine.observarConectividad(scope)
        repeat(3) { fixture.conectividad.emitir(true) }
        esperarHasta("el primer ciclo") { fixture.cuantasVecesSePidio(Endpoints.SYNC_CAMBIOS) >= 1 }
        margenParaDisparosDeMas()

        assertEquals(1, fixture.cuantasVecesSePidio(Endpoints.SYNC_CAMBIOS))
        observacion.cancel()
        scope.cancel()
    }

    @Test
    fun `el estado global vuelve a INACTIVO al terminar el ciclo`() = runTest {
        val fixture = FixtureDeSync { responderJson(cuerpoCambiosVacio()) }

        assertEquals(EstadoSync.INACTIVO, fixture.engine.estado.value)
        fixture.engine.ejecutarCiclo()
        assertEquals(EstadoSync.INACTIVO, fixture.engine.estado.value)
    }
}
