package com.ecolacteos.acopio.core

import kotlin.random.Random

/**
 * Generador de UUID v4 para los `uuidCliente` que el dispositivo asigna a toda captura offline
 * (`MOBILE_DATA_MAPPING.md §1.2`). El contrato de red modela `uuidCliente` como `String` en todos los
 * casos -- esta función devuelve exactamente ese formato: RFC 4122, minúsculas, con guiones
 * (`"550e8400-e29b-41d4-a716-446655440000"`), igual que `java.util.UUID.toString()` del lado del backend.
 *
 * Decisión (justificada en el checkpoint de Fase 1): implementación propia sobre `kotlin.random.Random`
 * en vez de `kotlin.uuid.Uuid`. `kotlin.uuid.Uuid` es experimental desde Kotlin 2.0.20 y
 * `MOBILE_DATA_MAPPING.md §1.2` ya evalúa y descarta atar el contrato de red a una API todavía inestable
 * del lenguaje -- una implementación propia de ~20 líneas es más auditable y no arrastra ese riesgo.
 */
fun generarUuidV4(random: Random = Random.Default): String {
    val bytes = ByteArray(16).also { random.nextBytes(it) }

    // Versión 4: los 4 bits altos del byte 6 fijos a 0100.
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    // Variante RFC 4122: los 2 bits altos del byte 8 fijos a 10.
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

    val hex = bytes.joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return buildString {
        append(hex, 0, 8)
        append('-')
        append(hex, 8, 12)
        append('-')
        append(hex, 12, 16)
        append('-')
        append(hex, 16, 20)
        append('-')
        append(hex, 20, 32)
    }
}

private val UUID_V4_REGEX =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

/** Valida que [texto] tenga el formato exacto de un UUID v4 RFC 4122 en minúsculas. */
fun esUuidV4Valido(texto: String): Boolean = UUID_V4_REGEX.matches(texto)
