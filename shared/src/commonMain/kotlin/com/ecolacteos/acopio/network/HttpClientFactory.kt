package com.ecolacteos.acopio.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json

/**
 * Construye el único `HttpClient` de la app, con el engine real de la plataforma
 * (`PROMPT_FASE_02.md §3`).
 */
fun crearHttpClient(
    apiConfig: ApiConfig,
    tokenProvider: TokenProvider,
    debug: Boolean = false,
): HttpClient = HttpClient(engineDePlataforma()) {
    configurarPluginsComunes(apiConfig, tokenProvider, debug)
}

/**
 * Todo lo que pide `PROMPT_FASE_02.md §3` salvo el engine: `ContentNegotiation` con [jsonApi], timeouts
 * explícitos pensados para redes rurales, el interceptor de autenticación (plugin `Auth`/`bearer`, jamás en
 * `POST /api/auth/login`) y logging condicional. Extraído a una función de extensión sobre
 * [HttpClientConfig] (en vez de vivir dentro de [crearHttpClient]) para que los tests puedan reusar
 * exactamente esta configuración con `MockEngine` en lugar del engine real de plataforma -- sin esto,
 * cualquier test de `ApiClient` estaría probando una config de cliente distinta de la que corre en producción.
 *
 * El interceptor de errores (mapeo uniforme a [com.ecolacteos.acopio.core.ApiError]) **no** vive acá --
 * vive en [ApiClient], porque necesita devolver `ApiResult` en vez de lanzar, y eso se resuelve mejor
 * inspeccionando la respuesta después de la llamada que con un plugin de Ktor (ver `ApiErrorMapper.kt`).
 *
 * @param debug Solo en este caso se instala el logging de requests -- nunca en producción. El logging
 *   nunca imprime el body (ver [LogLevel.HEADERS]) y sanitiza el header `Authorization`, así que ni el
 *   token ni las credenciales de login pueden aparecer en el log aunque `debug` esté prendido.
 */
fun HttpClientConfig<*>.configurarPluginsComunes(
    apiConfig: ApiConfig,
    tokenProvider: TokenProvider,
    debug: Boolean = false,
) {
    expectSuccess = false // los códigos de error se inspeccionan a mano en ApiClient, no vía excepciones

    install(ContentNegotiation) {
        json(jsonApi)
    }

    install(HttpTimeout) {
        connectTimeoutMillis = apiConfig.timeoutConexionMs
        requestTimeoutMillis = apiConfig.timeoutRequestMs
        socketTimeoutMillis = apiConfig.timeoutSocketMs
    }

    install(Auth) {
        bearer {
            loadTokens {
                tokenProvider.tokenActual()?.let { BearerTokens(accessToken = it, refreshToken = "") }
            }
            // El único endpoint público de toda la API (MOBILE_ARCHITECTURE.md §4) -- nunca lleva el header.
            sendWithoutRequest { request -> !request.url.build().encodedPath.endsWith(Endpoints.LOGIN) }
        }
    }

    if (debug) {
        install(Logging) {
            level = LogLevel.HEADERS // nunca BODY/ALL: el body del login (password) no debe poder loguearse
            sanitizeHeader { header -> header.equals("Authorization", ignoreCase = true) }
        }
    }
}
