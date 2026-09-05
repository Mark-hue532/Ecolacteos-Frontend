package com.ecolacteos.acopio.network

import com.ecolacteos.acopio.core.ApiError
import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.core.decimalDesdeTexto
import com.ecolacteos.acopio.data.remote.dto.LoginRequest
import com.ecolacteos.acopio.data.remote.dto.LoginResponse
import com.ecolacteos.acopio.data.remote.dto.RegistroAcopioDTO
import com.ecolacteos.acopio.data.remote.dto.SyncResultResponse
import com.ecolacteos.acopio.data.remote.dto.UnidadResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests de `ApiClient` con `MockEngine` (`PROMPT_FASE_02.md §6`), sobre la MISMA configuración de plugins
 * que corre en producción (`configurarPluginsComunes`, ver `HttpClientFactory.kt`) -- no una versión
 * simplificada aparte.
 */
class ApiClientTest {

    private val apiConfigBase = ApiConfig(entorno = Entorno.DEV, baseUrl = "https://api.test")

    private fun clienteConMock(
        tokenProvider: TokenProvider = TokenProviderEnMemoria(),
        apiConfig: ApiConfig = apiConfigBase,
        handler: MockRequestHandler,
    ): ApiClient {
        val httpClient = HttpClient(MockEngine(handler)) {
            configurarPluginsComunes(apiConfig, tokenProvider, debug = false)
        }
        return ApiClient(httpClient, apiConfig)
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `el header Authorization va en un request cualquiera`() = runTest {
        var headerCapturado: String? = null
        val cliente = clienteConMock(tokenProvider = TokenProviderEnMemoria("token-123")) { request ->
            headerCapturado = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"id":"p-1","placa":"ABC-1","responsableId":"r-1","responsableNombre":"Ana"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        cliente.get<UnidadResponse>(Endpoints.PROVEEDORES_OPERATIVO)

        assertEquals("Bearer token-123", headerCapturado)
    }

    @Test
    fun `el header Authorization NO va en el login`() = runTest {
        var headerCapturado: String? = "sin-tocar"
        val cliente = clienteConMock(tokenProvider = TokenProviderEnMemoria("token-123")) { request ->
            headerCapturado = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"token":"t","rol":"ACOPIADOR","nombre":"Ana","expiraEnSegundos":28800}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        cliente.post<LoginRequest, LoginResponse>(Endpoints.LOGIN, LoginRequest("a@b.com", "x"))

        assertEquals(null, headerCapturado)
    }

    @Test
    fun `el body de POST sync-registros-acopio se serializa como array crudo -- no como objeto envolvente`() = runTest {
        var bodyCapturado: String? = null
        val cliente = clienteConMock { request ->
            bodyCapturado = (request.body as TextContent).text
            respond(
                content = """{"confirmados":[],"errores":[]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val registro = RegistroAcopioDTO(
            uuidCliente = "uc-1",
            proveedorId = "p-1",
            unidadId = "u-1",
            fechaHora = LocalDateTime(2026, 9, 4, 6, 0, 0),
            litros = decimalDesdeTexto("100.00"),
        )
        cliente.postLista<RegistroAcopioDTO, SyncResultResponse>(Endpoints.SYNC_REGISTROS_ACOPIO, listOf(registro))

        assertTrue(bodyCapturado!!.startsWith("["), "el body debe empezar con '[', no con '{': $bodyCapturado")
        assertTrue(
            bodyCapturado!!.contains(""""fechaHora":"2026-09-04T06:00:00""""),
            "fechaHora con segundos en cero debe ir explicita, no omitida: $bodyCapturado",
        )
    }

    @Test
    fun `400 produce ErrorValidacion no transitorio -- con el mensaje literal del backend`() = runTest {
        val cliente = clienteConMock { _ ->
            respond(
                content = """{"timestamp":"2026-09-04T10:00:00Z","status":400,"error":"Bad Request","mensaje":"litros debe ser mayor a cero"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders,
            )
        }

        val resultado = cliente.get<UnidadResponse>("/cualquiera")

        val error = assertIs<ApiResult.Error>(resultado)
        val apiError = assertIs<ApiError.ErrorValidacion>(error.error)
        assertEquals("litros debe ser mayor a cero", apiError.mensaje)
        assertFalse(apiError.esTransitorio)
    }

    @Test
    fun `401 produce NoAutorizado no transitorio`() = runTest {
        val cliente = clienteConMock { _ ->
            respond(
                content = """{"timestamp":"t","status":401,"error":"Unauthorized","mensaje":"Token invalido"}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders,
            )
        }
        val resultado = cliente.get<UnidadResponse>("/cualquiera")
        val error = assertIs<ApiResult.Error>(resultado)
        assertIs<ApiError.NoAutorizado>(error.error)
        assertFalse(error.error.esTransitorio)
    }

    @Test
    fun `403 produce SinPermiso no transitorio`() = runTest {
        val cliente = clienteConMock { _ ->
            respond(
                content = """{"timestamp":"t","status":403,"error":"Forbidden","mensaje":"Sin permiso"}""",
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders,
            )
        }
        val resultado = cliente.get<UnidadResponse>("/cualquiera")
        val error = assertIs<ApiResult.Error>(resultado)
        assertIs<ApiError.SinPermiso>(error.error)
        assertFalse(error.error.esTransitorio)
    }

    @Test
    fun `404 produce NoEncontrado no transitorio`() = runTest {
        val cliente = clienteConMock { _ ->
            respond(
                content = """{"timestamp":"t","status":404,"error":"Not Found","mensaje":"No existe"}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }
        val resultado = cliente.get<UnidadResponse>("/cualquiera")
        val error = assertIs<ApiResult.Error>(resultado)
        assertIs<ApiError.NoEncontrado>(error.error)
        assertFalse(error.error.esTransitorio)
    }

    @Test
    fun `409 produce Conflicto no transitorio`() = runTest {
        val cliente = clienteConMock { _ ->
            respond(
                content = """{"timestamp":"t","status":409,"error":"Conflict","mensaje":"Ya existe una recepcion para esa fecha"}""",
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders,
            )
        }
        val resultado = cliente.get<UnidadResponse>("/cualquiera")
        val error = assertIs<ApiResult.Error>(resultado)
        assertIs<ApiError.Conflicto>(error.error)
        assertFalse(error.error.esTransitorio)
    }

    @Test
    fun `422 produce ErrorValidacion no transitorio`() = runTest {
        val cliente = clienteConMock { _ ->
            respond(
                content = """{"timestamp":"t","status":422,"error":"Unprocessable Entity","mensaje":"tipoCliente invalido"}""",
                status = HttpStatusCode.UnprocessableEntity,
                headers = jsonHeaders,
            )
        }
        val resultado = cliente.get<UnidadResponse>("/cualquiera")
        val error = assertIs<ApiResult.Error>(resultado)
        assertIs<ApiError.ErrorValidacion>(error.error)
        assertFalse(error.error.esTransitorio)
    }

    @Test
    fun `500 produce ErrorServidor transitorio`() = runTest {
        val cliente = clienteConMock { _ ->
            respond(
                content = """{"timestamp":"t","status":500,"error":"Internal Server Error","mensaje":"Error interno"}""",
                status = HttpStatusCode.InternalServerError,
                headers = jsonHeaders,
            )
        }
        val resultado = cliente.get<UnidadResponse>("/cualquiera")
        val error = assertIs<ApiResult.Error>(resultado)
        assertIs<ApiError.ErrorServidor>(error.error)
        assertTrue(error.error.esTransitorio)
    }

    @Test
    fun `codigo no contemplado produce Desconocido no transitorio`() = runTest {
        val cliente = clienteConMock { _ ->
            respond(
                content = """{"timestamp":"t","status":418,"error":"I'm a teapot","mensaje":"?"}""",
                status = HttpStatusCode(418, "I'm a teapot"),
                headers = jsonHeaders,
            )
        }
        val resultado = cliente.get<UnidadResponse>("/cualquiera")
        val error = assertIs<ApiResult.Error>(resultado)
        assertIs<ApiError.Desconocido>(error.error)
        assertFalse(error.error.esTransitorio)
    }

    @Test
    fun `un cuerpo de error que no es JSON valido no rompe el cliente`() = runTest {
        val cliente = clienteConMock { _ ->
            respond(
                content = "<html><body>502 Bad Gateway</body></html>",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }
        val resultado = cliente.get<UnidadResponse>("/cualquiera")
        val error = assertIs<ApiResult.Error>(resultado)
        assertIs<ApiError.ErrorServidor>(error.error)
    }

    @Test
    fun `un timeout produce un error transitorio -- no permanente`() = runTest {
        val configCorto = ApiConfig(
            entorno = Entorno.DEV,
            baseUrl = "https://api.test",
            timeoutRequestMs = 50,
            timeoutConexionMs = 50,
            timeoutSocketMs = 50,
        )
        val cliente = clienteConMock(apiConfig = configCorto) { _ ->
            delay(500) // más que el timeout configurado
            respond(
                content = """{"id":"p-1","placa":"ABC-1","responsableId":"r-1","responsableNombre":"Ana"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val resultado = cliente.get<UnidadResponse>("/cualquiera")

        val error = assertIs<ApiResult.Error>(resultado)
        assertTrue(error.error.esTransitorio, "un timeout debe ser transitorio, fue: ${error.error}")
    }

    @Test
    fun `un campo desconocido en la respuesta no rompe la deserializacion`() = runTest {
        val cliente = clienteConMock { _ ->
            respond(
                content = """{"id":"p-1","placa":"ABC-1","responsableId":"r-1","responsableNombre":"Ana","campoNuevo":true}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val resultado = cliente.get<UnidadResponse>("/cualquiera")

        val exito = assertIs<ApiResult.Exito<UnidadResponse>>(resultado)
        assertEquals("ABC-1", exito.datos.placa)
    }
}
