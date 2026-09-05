package com.ecolacteos.acopio.network

import com.ecolacteos.acopio.domain.GestorSesion

/**
 * `TokenProvider` real de la Fase 3: el JWT vigente es el que tiene la sesión de [GestorSesion], que a su
 * vez lo lee de `SecureTokenStorage`. Reemplaza a [TokenProviderEnMemoria] en el binding de Koin
 * (`di/SecurityModule.kt`) -- esa implementación queda solo para tests, como preveía el comentario de la
 * Fase 2 en `TokenProvider.kt`.
 *
 * [gestorSesion] es `Lazy`, no un `GestorSesion` directo, **a propósito**: hay un ciclo de construcción en
 * el grafo de Koin que no es obvio hasta que se lo tropieza -- `HttpClient` necesita un `TokenProvider`
 * (`HttpClientFactory.kt`), este `TokenProvider` necesita `GestorSesion`, y `GestorSesion` necesita
 * `ApiClient`, que a su vez necesita el mismísimo `HttpClient`. Resolver `GestorSesion` de manera *ansiosa*
 * en el constructor reproduce ese ciclo (`StackOverflowError` en Koin al armar el grafo, se ve en
 * `CoreModuleTest`). Con `Lazy`, Koin construye este `TokenProvider` sin tocar `GestorSesion` todavía --
 * recién se resuelve la primera vez que [tokenActual] se llama de verdad, en runtime, momento en el que
 * todo el grafo ya terminó de armarse.
 */
class TokenProviderSobreGestorSesion(private val gestorSesion: Lazy<GestorSesion>) : TokenProvider {
    override suspend fun tokenActual(): String? = gestorSesion.value.sesionActual()?.token
}
