package com.ecolacteos.acopio.network

/** Entornos del backend contra los que puede apuntar la app. */
enum class Entorno {
    LOCAL,
    DEV,
    STAGING,
    PROD,
}

/**
 * URL base y timeouts por entorno (`PROMPT_FASE_02.md §3` -- "URL base por entorno, no una constante
 * suelta"). Se inyecta por Koin como una instancia única elegida al arrancar la app; cambiar de entorno es
 * cambiar qué [ApiConfig] provee el módulo de Koin, no tocar código de red.
 *
 * ⚠️ **TODO -- dominios inventados**: las URLs de `DEV`/`STAGING`/`PROD` de abajo son placeholders, nadie
 * dio todavía el dominio real (documentado en el checkpoint de la Fase 2). El mecanismo (selección por
 * [Entorno], objeto único inyectado) es lo que esta fase deja resuelto; la URL real de cada entorno se
 * corrige acá mismo cuando infra la confirme, sin tocar ningún otro archivo.
 *
 * `LOCAL` no es un TODO: `http://10.0.2.2:8080` es la loopback fija con la que el emulador de Android ve
 * `localhost` de la máquina host (no un placeholder a reemplazar), para levantar el backend en la propia
 * máquina de desarrollo y probar contra él. No sirve desde un dispositivo físico ni desde el simulador de
 * iOS (loopback real ahí es `127.0.0.1`); es solo para el emulador de Android.
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
            Entorno.LOCAL -> ApiConfig(entorno = Entorno.LOCAL, baseUrl = "http://10.0.2.2:8080")
            // TODO: dominio real sin confirmar -- ver checkpoint Fase 2.
            Entorno.DEV -> ApiConfig(entorno = Entorno.DEV, baseUrl = "https://dev.api.ecolacteos.pe")
            // TODO: dominio real sin confirmar -- ver checkpoint Fase 2.
            Entorno.STAGING -> ApiConfig(entorno = Entorno.STAGING, baseUrl = "https://staging.api.ecolacteos.pe")
            // TODO: dominio real sin confirmar -- ver checkpoint Fase 2.
            Entorno.PROD -> ApiConfig(entorno = Entorno.PROD, baseUrl = "https://api.ecolacteos.pe")
        }
    }
}
