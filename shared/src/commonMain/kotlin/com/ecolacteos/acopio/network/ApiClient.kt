package com.ecolacteos.acopio.network

import com.ecolacteos.acopio.core.ApiError
import com.ecolacteos.acopio.core.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/** Resultado interno de ejecutar la llamada HTTP, antes de decodificar el body a un tipo concreto. */
@PublishedApi
internal sealed class ResultadoLlamada {
    class Ok(val response: HttpResponse) : ResultadoLlamada()
    class Falla(val error: ApiError) : ResultadoLlamada()
}

/**
 * Ejecuta [llamada] y clasifica cualquier fallo de red/timeout, sin tocar el body todavía -- la
 * decodificación al tipo concreto queda en las funciones `reified` de [ApiClient], porque esta función no
 * necesita ser `inline` (no maneja el tipo de respuesta) y así evita que dos funciones `inline` con `reified`
 * se llamen entre sí dentro de la misma clase.
 */
@PublishedApi
internal suspend fun ejecutarLlamadaHttp(llamada: suspend () -> HttpResponse): ResultadoLlamada {
    val response = try {
        llamada()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        return ResultadoLlamada.Falla(ApiError.Timeout(e.message ?: "Tiempo de espera agotado"))
    } catch (e: ConnectTimeoutException) {
        return ResultadoLlamada.Falla(ApiError.Timeout(e.message ?: "Tiempo de espera agotado"))
    } catch (e: SocketTimeoutException) {
        return ResultadoLlamada.Falla(ApiError.Timeout(e.message ?: "Tiempo de espera agotado"))
    } catch (e: Exception) {
        return ResultadoLlamada.Falla(ApiError.SinConexion(e.message ?: "Sin conectividad"))
    }

    return if (response.status.isSuccess()) {
        ResultadoLlamada.Ok(response)
    } else {
        ResultadoLlamada.Falla(mapearErrorHttp(response))
    }
}

/**
 * Decodifica el body de una [ResultadoLlamada.Ok] al tipo [T], o propaga el error ya clasificado. Nunca
 * deja escapar una excepción de Ktor ni de kotlinx.serialization -- ver `CLAUDE.md §3.4`.
 */
@PublishedApi
internal suspend inline fun <reified T> ResultadoLlamada.aApiResult(): ApiResult<T> = when (this) {
    is ResultadoLlamada.Falla -> ApiResult.Error(error)
    is ResultadoLlamada.Ok -> try {
        ApiResult.Exito(response.body<T>())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.Error(ApiError.Desconocido("No se pudo interpretar la respuesta del servidor"))
    }
}

/**
 * Capa genérica sobre [HttpClient] que la app usa para hablar con el backend (`PROMPT_FASE_02.md §3-5`).
 * Nunca deja escapar una excepción de Ktor: todo lo que sale de acá es [ApiResult] (`CLAUDE.md §3.4` --
 * la UI y los UseCases no ven `HttpClient` ni sus excepciones).
 *
 * Las funciones de negocio (una por endpoint, con sus DTOs concretos) son responsabilidad de los
 * `Repository` de la Fase 6 -- acá solo está la mecánica HTTP compartida por todos ellos.
 */
class ApiClient(
    @PublishedApi internal val httpClient: HttpClient,
    @PublishedApi internal val apiConfig: ApiConfig,
) {
    /** `GET` simple, con query params opcionales. */
    suspend inline fun <reified T> get(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
    ): ApiResult<T> = ejecutarLlamadaHttp {
        httpClient.get(apiConfig.baseUrl + path) {
            queryParams.forEach { (nombre, valor) -> parameter(nombre, valor) }
        }
    }.aApiResult()

    /** `POST` con un body de objeto único. */
    suspend inline fun <reified TReq, reified TRes> post(
        path: String,
        body: TReq,
    ): ApiResult<TRes> = ejecutarLlamadaHttp {
        httpClient.post(apiConfig.baseUrl + path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.aApiResult()

    /**
     * `POST` cuyo body es un **array JSON crudo**, no un objeto envolvente -- los 4 endpoints
     * Los 4 endpoints POST de sync (`MOBILE_DATA_MAPPING.md §5.6`). `setBody(lista)` con `List<TReq>` reificado
     * serializa exactamente como `[ {...}, {...} ]`, nunca `{"items": [...]}`.
     */
    suspend inline fun <reified TReq, reified TRes> postLista(
        path: String,
        lista: List<TReq>,
    ): ApiResult<TRes> = ejecutarLlamadaHttp {
        httpClient.post(apiConfig.baseUrl + path) {
            contentType(ContentType.Application.Json)
            setBody(lista)
        }
    }.aApiResult()
}
