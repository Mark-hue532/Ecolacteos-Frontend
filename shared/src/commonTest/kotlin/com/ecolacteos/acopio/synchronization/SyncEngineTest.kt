package com.ecolacteos.acopio.synchronization

import com.ecolacteos.acopio.domain.model.LoteProduccionRegistro
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.network.Endpoints
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Tests del Sync Engine (`MOBILE_ARCHITECTURE.md §17`, filas de sincronización). */
class SyncEngineTest {

    // ---------------------------------------------------------------------------------------------
    // Reconciliación del lote (§6.2)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `lote mixto -- confirmado va a SYNCED -- con error va a FAILED -- y el ausente se queda SYNCING`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson(cuerpoSync(confirmados = listOf("ok"), errores = mapOf("malo" to "proveedor inactivo")))
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("ok")
        fixture.sembrarRegistro("malo")
        fixture.sembrarRegistro("omitido")

        val resultado = fixture.engine.ejecutarCiclo()

        assertIs<ResultadoCiclo.Completado>(resultado)
        assertEquals(SyncStatus.SYNCED, fixture.registros.obtenerPorUuidCliente("ok")?.syncStatus)

        val conError = fixture.registros.obtenerPorUuidCliente("malo")
        assertEquals(SyncStatus.FAILED, conError?.syncStatus)
        assertEquals("proveedor inactivo", conError?.syncError)
        assertNull(conError?.nextAttemptAt, "un fallo permanente no programa reintento (§6.1)")

        // Trampa #2: nunca se asume éxito por omisión.
        assertEquals(SyncStatus.SYNCING, fixture.registros.obtenerPorUuidCliente("omitido")?.syncStatus)

        val resumen = resultado.resumen.porRecurso.getValue(RecursoSync.REGISTRO_ACOPIO)
        assertEquals(1, resumen.confirmados)
        assertEquals(1, resumen.fallidosPermanentes)
        assertEquals(1, resumen.sinRespuesta)
    }

    @Test
    fun `una fila confirmada queda SYNCED con sincronizado_en del dispositivo y sin server_id -- DATA-014`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson(cuerpoSync(confirmados = listOf("reg-1")))
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("reg-1")

        fixture.engine.ejecutarCiclo()

        val fila = fixture.registros.obtenerPorUuidCliente("reg-1")
        assertEquals(SyncStatus.SYNCED, fila?.syncStatus)
        assertNull(fila?.serverId, "el lote confirma por uuidCliente y no devuelve el id de Postgres")
        // Hora de pared del dispositivo al recibir la confirmación (el contrato no da la del servidor).
        assertEquals(LocalDateTime(2026, 9, 5, 12, 0, 0), fila?.sincronizadoEn)
    }

    @Test
    fun `reenviar un lote que el backend ya tenia no duplica ni pierde la fila local`() = runTest {
        // El backend idempotente responde "confirmado" igual la segunda vez (§6.4/§7).
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson(cuerpoSync(confirmados = listOf("reg-dup")))
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("reg-dup", estado = SyncStatus.SYNCING) // quedó "en vuelo" de un ciclo previo

        fixture.engine.ejecutarCiclo()
        fixture.engine.ejecutarCiclo()

        assertEquals(SyncStatus.SYNCED, fixture.registros.obtenerPorUuidCliente("reg-dup")?.syncStatus)
        val filas = fixture.registros.observarTodos(GestorSesionFake.USUARIO_ID).first()
        assertEquals(1, filas.size, "el reenvío no crea una segunda fila local")
    }

    @Test
    fun `una fila SYNCING huerfana al arrancar se reintenta -- no queda trabada`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_VENTAS) {
                responderJson(cuerpoSync(confirmados = listOf("venta-huerfana")))
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarVenta("venta-huerfana", estado = SyncStatus.SYNCING)

        fixture.engine.ejecutarCiclo()

        assertEquals(SyncStatus.SYNCED, fixture.ventas.obtenerPorUuidCliente("venta-huerfana")?.syncStatus)
    }

    // ---------------------------------------------------------------------------------------------
    // Dependencias (§18.1) -- el corazón de la fase
    // ---------------------------------------------------------------------------------------------

    /**
     * El test que exige `PROMPT_FASE_05.md §Testing`. El padre arranca `PENDING` **con `server_id` ya
     * conocido**: es la única forma de ejercer la promoción real hoy, porque el lote no devuelve ids
     * (`DATA-014`) -- ese caso, el de verdad frecuente, lo cubre el test siguiente.
     */
    @Test
    fun `el hijo pasa de PENDING_DEPENDENCY a PENDING en el MISMO ciclo que confirma al padre`() = runTest {
        val fixture = FixtureDeSync { request ->
            when (request.url.encodedPath) {
                Endpoints.SYNC_REGISTROS_ACOPIO -> responderJson(cuerpoSync(confirmados = listOf("padre")))
                Endpoints.SYNC_ANALISIS_CALIDAD -> responderJson(cuerpoSync(confirmados = listOf("hijo")))
                else -> responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("padre", serverId = "server-del-padre")
        fixture.sembrarAnalisis("hijo", padreUuidCliente = "padre", estado = SyncStatus.PENDING_DEPENDENCY)

        val resultado = fixture.engine.ejecutarCiclo()

        assertIs<ResultadoCiclo.Completado>(resultado)
        assertEquals(SyncStatus.SYNCED, fixture.registros.obtenerPorUuidCliente("padre")?.syncStatus)

        val hijo = fixture.analisis.obtenerPorUuidCliente("hijo")
        assertEquals(SyncStatus.PENDING, hijo?.syncStatus, "promovido en este ciclo")
        assertEquals(1, resultado.resumen.promovidosPorDependencia)
        // No se reenvía en este ciclo: el POST de análisis no llegó a pedirse (nada era enviable al empezar).
        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.SYNC_ANALISIS_CALIDAD))
    }

    /** El caso real de hoy: el padre sincroniza, pero sin id no se puede armar el request del hijo. */
    @Test
    fun `si el padre queda SYNCED sin server_id el hijo sigue en PENDING_DEPENDENCY con motivo DATA-014`() = runTest {
        val fixture = FixtureDeSync { request ->
            when (request.url.encodedPath) {
                Endpoints.SYNC_REGISTROS_ACOPIO -> responderJson(cuerpoSync(confirmados = listOf("padre")))
                else -> responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("padre")
        fixture.sembrarAnalisis("hijo", padreUuidCliente = "padre", estado = SyncStatus.PENDING_DEPENDENCY)

        val resultado = fixture.engine.ejecutarCiclo()

        assertIs<ResultadoCiclo.Completado>(resultado)
        val hijo = fixture.analisis.obtenerPorUuidCliente("hijo")
        assertEquals(SyncStatus.PENDING_DEPENDENCY, hijo?.syncStatus)
        assertEquals(0, resultado.resumen.promovidosPorDependencia)
        assertEquals(1, resultado.resumen.bloqueadosPorIdDePadre)
        assertContains(hijo?.syncError.orEmpty(), "DATA-014")
    }

    @Test
    fun `un hijo con padre ajeno ya resuelto se sincroniza sin pasar nunca por PENDING_DEPENDENCY`() = runTest {
        val fixture = FixtureDeSync { request ->
            when (request.url.encodedPath) {
                Endpoints.SYNC_ANALISIS_CALIDAD -> responderJson(cuerpoSync(confirmados = listOf("hijo-ajeno")))
                else -> responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarAnalisis("hijo-ajeno", padreServerId = "server-de-otro-dispositivo")

        val resultado = fixture.engine.ejecutarCiclo()

        assertIs<ResultadoCiclo.Completado>(resultado)
        assertEquals(SyncStatus.SYNCED, fixture.analisis.obtenerPorUuidCliente("hijo-ajeno")?.syncStatus)
        assertEquals(0, resultado.resumen.porRecurso.getValue(RecursoSync.ANALISIS_CALIDAD).enEsperaDeDependencia)
    }

    @Test
    fun `un PENDING cuyo padre no sincronizo todavia cae a PENDING_DEPENDENCY y no se envia`() = runTest {
        val fixture = FixtureDeSync { request ->
            when (request.url.encodedPath) {
                Endpoints.SYNC_REGISTROS_ACOPIO -> responderJson(cuerpoSync()) // el padre no se confirma
                else -> responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("padre")
        fixture.sembrarAnalisis("hijo", padreUuidCliente = "padre", estado = SyncStatus.PENDING)

        fixture.engine.ejecutarCiclo()

        val hijo = fixture.analisis.obtenerPorUuidCliente("hijo")
        assertEquals(SyncStatus.PENDING_DEPENDENCY, hijo?.syncStatus)
        assertContains(hijo?.syncError.orEmpty(), "Esperando")
        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.SYNC_ANALISIS_CALIDAD))
    }

    @Test
    fun `un lote espera si alguno de sus registros no resuelve -- no se envia a medias`() = runTest {
        val fixture = FixtureDeSync { responderJson(cuerpoCambiosVacio()) }
        fixture.sembrarRegistro("padre-sin-sincronizar")
        fixture.sembrarLote("lote-1")
        fixture.lotes.insertarRegistro(LoteProduccionRegistro("lote-1", null, "server-resuelto"))
        fixture.lotes.insertarRegistro(LoteProduccionRegistro("lote-1", "padre-sin-sincronizar", null))

        fixture.engine.ejecutarCiclo()

        assertEquals(SyncStatus.PENDING_DEPENDENCY, fixture.lotes.obtenerPorUuidCliente("lote-1")?.syncStatus)
        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.SYNC_LOTES_PRODUCCION))
    }

    /** Trampa #1: si el orden entre recursos estuviera mal, esto seguiría "funcionando" un ciclo más tarde. */
    @Test
    fun `RegistroAcopio se procesa antes que sus hijos dentro del ciclo`() = runTest {
        val fixture = FixtureDeSync { request ->
            when (request.url.encodedPath) {
                Endpoints.SYNC_REGISTROS_ACOPIO -> responderJson(cuerpoSync(confirmados = listOf("padre")))
                Endpoints.SYNC_ANALISIS_CALIDAD -> responderJson(cuerpoSync(confirmados = listOf("hijo")))
                else -> responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("padre", serverId = "server-del-padre")
        fixture.sembrarAnalisis("hijo", padreUuidCliente = "padre")

        fixture.engine.ejecutarCiclo()

        val posicionRegistros = fixture.rutasPedidas.indexOf(Endpoints.SYNC_REGISTROS_ACOPIO)
        val posicionAnalisis = fixture.rutasPedidas.indexOf(Endpoints.SYNC_ANALISIS_CALIDAD)
        assertTrue(posicionRegistros in 0 until posicionAnalisis, "orden real: ${fixture.rutasPedidas}")
        // Y `/sync/cambios` va último, después de los 4 recursos.
        assertEquals(Endpoints.SYNC_CAMBIOS, fixture.rutasPedidas.last())
    }

    // ---------------------------------------------------------------------------------------------
    // Reintentos y backoff (§6.3)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `un 500 es transitorio -- incrementa intentos y programa next_attempt_at con la secuencia elegida`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson("""{"mensaje":"boom"}""", HttpStatusCode.InternalServerError)
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("reg-1")

        val resultado = fixture.engine.ejecutarCiclo()

        val fila = fixture.registros.obtenerPorUuidCliente("reg-1")
        assertEquals(SyncStatus.FAILED, fila?.syncStatus)
        assertEquals(1, fila?.syncAttempts)
        // Primer reintento de la secuencia documentada: 15s sobre el reloj fijo (12:00:00Z).
        assertEquals(LocalDateTime(2026, 9, 5, 12, 0, 15), fila?.nextAttemptAt)
        assertIs<ResultadoCiclo.Completado>(resultado)
        assertEquals(1, resultado.resumen.porRecurso.getValue(RecursoSync.REGISTRO_ACOPIO).fallidosTransitorios)
    }

    @Test
    fun `un timeout es transitorio -- nunca permanente`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                throw io.ktor.client.plugins.HttpRequestTimeoutException(request)
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("reg-1")

        val resultado = fixture.engine.ejecutarCiclo()

        val fila = fixture.registros.obtenerPorUuidCliente("reg-1")
        assertEquals(SyncStatus.FAILED, fila?.syncStatus)
        assertNotNull(fila?.nextAttemptAt, "un timeout debe quedar reintentable")
        assertIs<ResultadoCiclo.Completado>(resultado)
        assertEquals(1, resultado.resumen.porRecurso.getValue(RecursoSync.REGISTRO_ACOPIO).fallidosTransitorios)
    }

    @Test
    fun `un 422 es permanente -- no programa reintento`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_VENTAS) {
                responderJson("""{"mensaje":"cantidad invalida"}""", HttpStatusCode.UnprocessableEntity)
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarVenta("venta-1")

        fixture.engine.ejecutarCiclo()

        val fila = fixture.ventas.obtenerPorUuidCliente("venta-1")
        assertEquals(SyncStatus.FAILED, fila?.syncStatus)
        assertNull(fila?.nextAttemptAt)
    }

    @Test
    fun `una fila con backoff todavia vigente no se reenvia en este ciclo`() = runTest {
        val fixture = FixtureDeSync { responderJson(cuerpoCambiosVacio()) }
        fixture.sembrarRegistro("reg-1")
        fixture.registros.actualizarEstadoSync(
            "reg-1",
            SyncStatus.FAILED,
            1,
            "500",
            LocalDateTime(2026, 9, 5, 12, 0, 15), // 15s en el futuro respecto del reloj fijo
        )

        fixture.engine.ejecutarCiclo()

        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.SYNC_REGISTROS_ACOPIO))
    }

    @Test
    fun `pasado el tope de intentos automaticos la fila deja de reintentarse sola`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson("""{"mensaje":"boom"}""", HttpStatusCode.InternalServerError)
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("reg-1", intentos = PoliticaDeSync.MAXIMO_INTENTOS_AUTOMATICOS - 1)

        fixture.engine.ejecutarCiclo()

        val fila = fixture.registros.obtenerPorUuidCliente("reg-1")
        assertEquals(SyncStatus.FAILED, fila?.syncStatus)
        assertEquals(PoliticaDeSync.MAXIMO_INTENTOS_AUTOMATICOS, fila?.syncAttempts)
        assertNull(fila?.nextAttemptAt, "agotado el tope, requiere revisión manual (§6.3)")
        assertContains(fila?.syncError.orEmpty(), "reintentos automáticos")
    }

    // ---------------------------------------------------------------------------------------------
    // Troceo, sesión y catálogos
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `un lote mas grande que el fragmento genera mas de un POST`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson(cuerpoSync())
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        repeat(PoliticaDeSync.TAMANO_FRAGMENTO + 1) { indice -> fixture.sembrarRegistro("reg-$indice") }

        val resultado = fixture.engine.ejecutarCiclo()

        assertEquals(2, fixture.cuantasVecesSePidio(Endpoints.SYNC_REGISTROS_ACOPIO))
        assertIs<ResultadoCiclo.Completado>(resultado)
        assertEquals(2, resultado.resumen.porRecurso.getValue(RecursoSync.REGISTRO_ACOPIO).fragmentosEnviados)
    }

    @Test
    fun `sin sesion activa el ciclo no toca la red`() = runTest {
        val fixture = FixtureDeSync(gestorSesion = GestorSesionFake(sesionFija = null)) {
            responderJson(cuerpoCambiosVacio())
        }
        fixture.sembrarRegistro("reg-1")

        assertEquals(ResultadoCiclo.SinSesion, fixture.engine.ejecutarCiclo())
        assertTrue(fixture.rutasPedidas.isEmpty())
    }

    @Test
    fun `un 401 aborta el ciclo entero como SesionInvalida -- distinto de un fallo reintentable`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson("""{"mensaje":"token vencido"}""", HttpStatusCode.Unauthorized)
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("reg-1")

        val resultado = fixture.engine.ejecutarCiclo()

        assertIs<ResultadoCiclo.SesionInvalida>(resultado)
        // El ciclo se corta: ni siquiera se piden los catálogos.
        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.SYNC_CAMBIOS))
        // La fila queda SYNCING: se reintenta sola tras el re-login, sin perder nada (§6.4).
        assertEquals(SyncStatus.SYNCING, fixture.registros.obtenerPorUuidCliente("reg-1")?.syncStatus)
    }

    @Test
    fun `los catalogos se refrescan aunque un recurso haya fallado`() = runTest {
        val fixture = FixtureDeSync { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson("""{"mensaje":"boom"}""", HttpStatusCode.InternalServerError)
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        fixture.sembrarRegistro("reg-1")

        val resultado = fixture.engine.ejecutarCiclo()

        assertIs<ResultadoCiclo.Completado>(resultado)
        assertTrue(resultado.resumen.catalogosActualizados)
        assertEquals(1, fixture.cuantasVecesSePidio(Endpoints.SYNC_CAMBIOS))
    }

    @Test
    fun `un ciclo sin nada pendiente igual refresca catalogos y no postea nada`() = runTest {
        val fixture = FixtureDeSync { responderJson(cuerpoCambiosVacio()) }

        val resultado = fixture.engine.ejecutarCiclo()

        assertIs<ResultadoCiclo.Completado>(resultado)
        assertEquals(listOf(Endpoints.SYNC_CAMBIOS), fixture.rutasPedidas)
    }
}
