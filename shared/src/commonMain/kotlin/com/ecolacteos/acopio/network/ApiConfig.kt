package com.ecolacteos.acopio.network

/** Entornos del backend contra los que puede apuntar la app. */
enum class Entorno {
    DEV,
    STAGING,
    PROD,
}

/**
 * URL base y timeouts por entorno (`PROMPT_FASE_02.md §3` -- "URL base por entorno, no una constante
 * suelta"). Se inyecta por Koin como una instancia única elegida al arrancar la app; cambiar de entorno es
 * cambiar qué [ApiConfig] provee el módulo de Koin, no tocar código de red.
 *
 * ⚠️ **Decisión pendiente de confirmar** (documentada en el checkpoint de la Fase 2): las URLs de abajo son
 * placeholders -- no hay todavía un dominio real asignado para `staging`/`prod`. El mecanismo (selección
 * por [Entorno], objeto único inyectado) es lo que esta fase deja resuelto; la URL real de cada entorno se
 * corrige acá mismo cuando infra la confirme, sin tocar ningún otro archivo.
 */
data class ApiConfig(
    val entorno: Entorno,
    val baseUrl: String,
    val timeoutConexionMs: Long = 10_000,
    val timeoutRequestMs: Long = 30_000,
    val timeoutSocketMs: Long = 30_000,
) {
    companion object {
        fun paraEntorno(entorno: Entorno): ApiConfig = when (entorno) {
            Entorno.DEV -> ApiConfig(entorno = Entorno.DEV, baseUrl = "https://dev.api.ecolacteos.pe")
            Entorno.STAGING -> ApiConfig(entorno = Entorno.STAGING, baseUrl = "https://staging.api.ecolacteos.pe")
            Entorno.PROD -> ApiConfig(entorno = Entorno.PROD, baseUrl = "https://api.ecolacteos.pe")
        }
    }
}
