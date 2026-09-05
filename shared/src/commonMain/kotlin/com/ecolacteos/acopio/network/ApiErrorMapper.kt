package com.ecolacteos.acopio.network

import com.ecolacteos.acopio.core.ApiError
import com.ecolacteos.acopio.core.ErrorResponse
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerializationException

/**
 * Mapeo uniforme de errores (`PROMPT_FASE_02.md §5`): toda respuesta de error de la API tiene la misma
 * forma [ErrorResponse] (`MOBILE_DATA_MAPPING.md §5.12`), incluidos los 401/403 que genera `SecurityConfig`
 * antes del `DispatcherServlet`. Este es el único punto del cliente que traduce un código HTTP a
 * [ApiError] -- nadie más en `shared/` vuelve a mirar un `HttpStatusCode` a mano.
 *
 * El `mensaje` del backend se conserva literal (nunca se reemplaza por texto propio): en los errores de
 * validación suele ser accionable y la UI lo muestra tal cual (`MOBILE_SCREENS.md §10.4`). Si el cuerpo no
 * es un [ErrorResponse] parseable (proxy caído, HTML de error, cuerpo vacío), no revienta: cae a un mensaje
 * genérico propio en vez de propagar la excepción de parseo.
 */
internal suspend fun mapearErrorHttp(response: HttpResponse): ApiError {
    val status = response.status.value
    val mensaje = mensajeDelCuerpo(response) ?: "Error del servidor (HTTP $status)"

    return when (status) {
        in 500..599 -> ApiError.ErrorServidor(mensaje, status)
        400, 422 -> ApiError.ErrorValidacion(mensaje, status)
        401 -> ApiError.NoAutorizado(mensaje)
        403 -> ApiError.SinPermiso(mensaje)
        404 -> ApiError.NoEncontrado(mensaje)
        409 -> ApiError.Conflicto(mensaje)
        else -> ApiError.Desconocido(mensaje, status)
    }
}

private suspend fun mensajeDelCuerpo(response: HttpResponse): String? {
    val texto = try {
        response.bodyAsText()
    } catch (e: Exception) {
        return null
    }
    if (texto.isBlank()) return null
    return try {
        jsonApi.decodeFromString(ErrorResponse.serializer(), texto).mensaje
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
