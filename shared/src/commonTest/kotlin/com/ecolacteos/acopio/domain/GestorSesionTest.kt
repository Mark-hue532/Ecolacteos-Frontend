package com.ecolacteos.acopio.domain

import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.data.remote.dto.Rol
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.ApiConfig
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.network.Entorno
import com.ecolacteos.acopio.network.SesionInvalidadaNotifier
import com.ecolacteos.acopio.network.TokenProviderEnMemoria
import com.ecolacteos.acopio.network.configurarPluginsComunes
import com.ecolacteos.acopio.security.AlmacenamientoSeguroDeSesionFake
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * `GestorSesionImpl` (`PROMPT_FASE_03.md §5-8`), con [AlmacenamientoSeguroDeSesionFake] y `MockEngine` --
 * corre en JVM y en iOS (`iosSimulatorArm64Test`), sin Keystore/Keychain real.
 */
class GestorSesionTest {

    // header {"alg":"HS256","typ":"JWT"}, payload {"sub":"ana@ecolacteos.pe","rol":"ACOPIADOR","usuarioId":"u-123"}
    private val tokenValido =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJzdWIiOiJhbmFAZWNvbGFjdGVvcy5wZSIsInJvbCI6IkFDT1BJQURPUiIsInVzdWFyaW9JZCI6InUtMTIzIn0" +
            ".firma-no-verificada"

    // mismo header/firma, payload sin el claim usuarioId
    private val tokenSinUsuarioId =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJzdWIiOiJhbmFAZWNvbGFjdGVvcy5wZSIsInJvbCI6IkFDT1BJQURPUiJ9" +
            ".firma-no-verificada"

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private class RelojFake(var ahora: Instant) : Clock {
        override fun now(): Instant = ahora
    }

    private fun construir(
        ahora: Instant = Instant.fromEpochMilliseconds(0),
        verificadorPendientes: VerificadorPendientes = VerificadorPendientesSinImplementar(),
        almacenamiento: AlmacenamientoSeguroDeSesionFake = AlmacenamientoSeguroDeSesionFake(),
        handler: MockRequestHandler,
    ): Pair<GestorSesionImpl, RelojFake> {
        val apiConfig = ApiConfig(entorno = Entorno.DEV, baseUrl = "https://api.test")
        val httpClient = HttpClient(MockEngine(handler)) {
            configurarPluginsComunes(apiConfig, TokenProviderEnMemoria(), debug = false)
        }
        val apiClient = ApiClient(httpClient, apiConfig, SesionInvalidadaNotifier())
        val reloj = RelojFake(ahora)
        return GestorSesionImpl(apiClient, almacenamiento, verificadorPendientes, reloj) to reloj
    }

    @Test
    fun `login exitoso persiste la sesion con la expiracion absoluta bien calculada`() = runTest {
        val ahora = Instant.fromEpochMilliseconds(1_000_000L)
        val (gestor, _) = construir(ahora = ahora) { _ ->
            respond(
                content = """{"token":"$tokenValido","rol":"ACOPIADOR","nombre":"Ana","expiraEnSegundos":28800}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val resultado = gestor.iniciarSesion("ana@ecolacteos.pe", "clave-cualquiera")

        val exito = assertIs<ApiResult.Exito<Sesion>>(resultado)
        assertEquals("u-123", exito.datos.usuarioId)
        assertEquals(1_000_000L + 28_800_000L, exito.datos.expiraEnEpochMillis)
        assertEquals(exito.datos, gestor.sesionActual())
        assertEquals(exito.datos, gestor.sesion.value)
    }

    @Test
    fun `login con token sin usuarioId no persiste nada y devuelve error`() = runTest {
        val almacenamiento = AlmacenamientoSeguroDeSesionFake()
        val (gestor, _) = construir(almacenamiento = almacenamiento) { _ ->
            respond(
                content = """{"token":"$tokenSinUsuarioId","rol":"ACOPIADOR","nombre":"Ana","expiraEnSegundos":28800}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val resultado = gestor.iniciarSesion("ana@ecolacteos.pe", "clave")

        assertIs<ApiResult.Error>(resultado)
        assertNull(almacenamiento.leer())
    }

    @Test
    fun `la contrasena no aparece en nada de lo persistido`() = runTest {
        val almacenamiento = AlmacenamientoSeguroDeSesionFake()
        val contrasena = "clave-super-secreta-123"
        val (gestor, _) = construir(almacenamiento = almacenamiento) { _ ->
            respond(
                content = """{"token":"$tokenValido","rol":"ACOPIADOR","nombre":"Ana","expiraEnSegundos":28800}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        gestor.iniciarSesion("ana@ecolacteos.pe", contrasena)

        val json = assertNotNull(almacenamiento.ultimoJsonGuardado, "debia haber persistido algo")
        assertFalse(json.contains(contrasena), "la contrasena no debe aparecer en lo persistido: $json")
        assertFalse(json.contains("password", ignoreCase = true))
    }

    @Test
    fun `401 limpia la sesion via invalidarSesion`() = runTest {
        val almacenamiento = AlmacenamientoSeguroDeSesionFake()
        almacenamiento.guardar(sesionDePrueba().aSesionPersistida())
        val (gestor, _) = construir(almacenamiento = almacenamiento) { _ ->
            respond(content = "no se usa en este test", status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        // El propio GestorSesion no lee el flow desde el almacenamiento hasta que se le pide -- forzamos
        // la carga inicial para simular una sesion ya activa antes del 401.
        gestor.sesionActual()

        gestor.invalidarSesion()

        assertNull(almacenamiento.leer())
        assertNull(gestor.sesion.value)
    }

    @Test
    fun `cerrarSesion con trabajo sin sincronizar no borra el token`() = runTest {
        val almacenamiento = AlmacenamientoSeguroDeSesionFake()
        almacenamiento.guardar(sesionDePrueba().aSesionPersistida())
        val (gestor, _) = construir(
            almacenamiento = almacenamiento,
            verificadorPendientes = object : VerificadorPendientes {
                override suspend fun hayTrabajoSinSincronizar() = true
            },
        ) { _ -> respond("no se usa", HttpStatusCode.OK, jsonHeaders) }

        val resultado = gestor.cerrarSesion()

        assertEquals(ResultadoCierreSesion.BLOQUEADA_POR_PENDIENTES, resultado)
        assertEquals(sesionDePrueba().aSesionPersistida(), almacenamiento.leer())
    }

    @Test
    fun `cerrarSesion sin trabajo pendiente borra el token`() = runTest {
        val almacenamiento = AlmacenamientoSeguroDeSesionFake()
        almacenamiento.guardar(sesionDePrueba().aSesionPersistida())
        val (gestor, _) = construir(almacenamiento = almacenamiento) { _ ->
            respond("no se usa", HttpStatusCode.OK, jsonHeaders)
        }

        val resultado = gestor.cerrarSesion()

        assertEquals(ResultadoCierreSesion.CERRADA, resultado)
        assertNull(almacenamiento.leer())
    }

    @Test
    fun `un token ya expirado no se intenta refrescar`() = runTest {
        var llamadasAlBackend = 0
        val ahora = Instant.fromEpochMilliseconds(10_000_000L)
        val almacenamiento = AlmacenamientoSeguroDeSesionFake()
        almacenamiento.guardar(sesionDePrueba(expiraEnEpochMillis = ahora.toEpochMilliseconds() - 1).aSesionPersistida())
        val (gestor, _) = construir(ahora = ahora, almacenamiento = almacenamiento) { _ ->
            llamadasAlBackend++
            respond(
                content = """{"token":"$tokenValido","rol":"ACOPIADOR","nombre":"Ana","expiraEnSegundos":28800}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val resultado = gestor.refrescarSiHaceFalta()

        assertIs<ApiResult.Exito<Unit>>(resultado)
        assertEquals(0, llamadasAlBackend, "un token vencido no debe intentar /api/auth/refresh")
    }

    @Test
    fun `con mas de 30 minutos de vigencia no se refresca`() = runTest {
        var llamadasAlBackend = 0
        val ahora = Instant.fromEpochMilliseconds(0)
        val almacenamiento = AlmacenamientoSeguroDeSesionFake()
        almacenamiento.guardar(sesionDePrueba(expiraEnEpochMillis = 31 * 60 * 1000L).aSesionPersistida()) // 31 min de vigencia
        val (gestor, _) = construir(ahora = ahora, almacenamiento = almacenamiento) { _ ->
            llamadasAlBackend++
            respond("no se usa", HttpStatusCode.OK, jsonHeaders)
        }

        gestor.refrescarSiHaceFalta()

        assertEquals(0, llamadasAlBackend)
    }

    @Test
    fun `con menos de 30 minutos de vigencia si se refresca`() = runTest {
        var llamadasAlBackend = 0
        val ahora = Instant.fromEpochMilliseconds(0)
        val almacenamiento = AlmacenamientoSeguroDeSesionFake()
        almacenamiento.guardar(sesionDePrueba(expiraEnEpochMillis = 29 * 60 * 1000L).aSesionPersistida()) // 29 min de vigencia
        val (gestor, _) = construir(ahora = ahora, almacenamiento = almacenamiento) { request ->
            llamadasAlBackend++
            assertTrue(request.url.encodedPath.endsWith(Endpoints.REFRESH))
            respond(
                content = """{"token":"$tokenValido","rol":"ACOPIADOR","nombre":"Ana","expiraEnSegundos":28800}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val resultado = gestor.refrescarSiHaceFalta()

        assertIs<ApiResult.Exito<Unit>>(resultado)
        assertEquals(1, llamadasAlBackend)
        assertEquals(28_800_000L, gestor.sesion.value?.expiraEnEpochMillis)
    }

    @Test
    fun `un fallo de red en el refresh no invalida la sesion existente`() = runTest {
        val ahora = Instant.fromEpochMilliseconds(0)
        val almacenamiento = AlmacenamientoSeguroDeSesionFake()
        val sesionOriginal = sesionDePrueba(expiraEnEpochMillis = 29 * 60 * 1000L)
        almacenamiento.guardar(sesionOriginal.aSesionPersistida())
        val (gestor, _) = construir(ahora = ahora, almacenamiento = almacenamiento) { _ ->
            respond("Error interno", HttpStatusCode.InternalServerError, jsonHeaders)
        }

        val resultado = gestor.refrescarSiHaceFalta()

        assertIs<ApiResult.Error>(resultado)
        assertEquals(sesionOriginal.aSesionPersistida(), almacenamiento.leer())
    }

    private fun sesionDePrueba(expiraEnEpochMillis: Long = 999_999_999_999L) = Sesion(
        token = tokenValido,
        usuarioId = "u-123",
        rol = Rol.ACOPIADOR,
        nombre = "Ana",
        expiraEnEpochMillis = expiraEnEpochMillis,
    )
}
