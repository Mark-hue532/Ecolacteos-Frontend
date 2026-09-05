package com.ecolacteos.acopio.network

/**
 * Fuente del JWT vigente para el interceptor de autenticación (`PROMPT_FASE_02.md §3`).
 *
 * La implementación real sobre Keystore/Keychain es de la Fase 3 (`SecureTokenStorage`), que todavía no
 * existe. Esta interfaz es el punto de indirección que permite construir el cliente HTTP ahora sin
 * acoplarlo a almacenamiento seguro -- la Fase 3 provee un `TokenProvider` real, esta fase solo consume la
 * interfaz.
 */
interface TokenProvider {
    /** El JWT actual, o `null` si no hay sesión iniciada (todavía no hizo login, o hizo logout). */
    suspend fun tokenActual(): String?
}

/**
 * Implementación en memoria -- para tests (`PROMPT_FASE_02.md §3`) y como default de Koin hasta que la
 * Fase 3 reemplace el binding por el `TokenProvider` real sobre almacenamiento seguro. No persiste nada:
 * un proceso nuevo siempre arranca sin token.
 */
class TokenProviderEnMemoria(private var token: String? = null) : TokenProvider {
    override suspend fun tokenActual(): String? = token

    fun establecerToken(nuevoToken: String?) {
        token = nuevoToken
    }
}
