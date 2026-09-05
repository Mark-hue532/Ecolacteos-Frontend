package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.ApiConfig
import com.ecolacteos.acopio.network.Entorno
import com.ecolacteos.acopio.network.SesionInvalidadaNotifier
import com.ecolacteos.acopio.network.TokenProvider
import com.ecolacteos.acopio.network.crearHttpClient
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * Módulo de red de la Fase 2 (`PROMPT_FASE_02.md §3`).
 *
 * Decisiones documentadas en el checkpoint de la fase:
 * - [Entorno.DEV] hardcodeado por ahora -- no hay todavía una pantalla/flag de selección de entorno
 *   (eso es UI, Fase 7). El mecanismo de [ApiConfig] ya soporta los 4 entornos; cambiar cuál usa la app
 *   en runtime es reemplazar esta única línea, o leerlo de una preferencia cuando exista almacenamiento
 *   de configuración.
 * - `debug = false` en [crearHttpClient] -- todavía no hay wiring de `BuildConfig.DEBUG`/equivalente
 *   iOS desde `androidApp`/`iosApp` hacia `shared`; queda para cuando exista ese mecanismo.
 *
 * El binding real de [TokenProvider] (sobre `GestorSesion`, que a su vez lee `SecureTokenStorage`) vive en
 * `securityModule` (Fase 3) -- [TokenProviderEnMemoria][com.ecolacteos.acopio.network.TokenProviderEnMemoria]
 * queda solo para tests, ya no se registra acá.
 */
val networkModule = module {
    single { ApiConfig.paraEntorno(Entorno.DEV) }
    single { SesionInvalidadaNotifier() }
    single<HttpClient> { crearHttpClient(apiConfig = get(), tokenProvider = get(), debug = false) }
    single { ApiClient(httpClient = get(), apiConfig = get(), sesionInvalidadaNotifier = get()) }
}
