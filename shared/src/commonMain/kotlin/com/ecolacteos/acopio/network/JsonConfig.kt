package com.ecolacteos.acopio.network

import kotlinx.serialization.json.Json

/**
 * La única instancia de `Json` del proyecto -- todo DTO se (de)serializa con esta configuración
 * (`PROMPT_FASE_02.md §3`).
 */
val jsonApi: Json = Json {
    // El backend puede agregar campos con el tiempo; que la app rompa por un campo nuevo que no le
    // interesa sería más frágil que ignorarlo.
    ignoreUnknownKeys = true
    // El backend emite JSON estricto -- ser laxo acá esconderia errores reales de contrato.
    isLenient = false
    encodeDefaults = true
    // Omite los null en el body en vez de mandarlos explícitos; el backend los trata igual que ausentes.
    explicitNulls = false
    // Nunca convertir un null en el valor por default en silencio -- si un campo no-nulo llega null,
    // es un problema de contrato que tiene que verse, no esconderse.
    coerceInputValues = false
}
