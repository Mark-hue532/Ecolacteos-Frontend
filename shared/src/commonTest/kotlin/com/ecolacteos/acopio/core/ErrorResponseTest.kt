package com.ecolacteos.acopio.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `ErrorResponse` es la forma única de error de toda la API MOBILE (`MOBILE_DATA_MAPPING.md §5.12`).
 * Este test deserializa el JSON real que devuelve el backend, no una fixture inventada -- si el backend
 * cambia esta forma, este test es el primero en romperse.
 */
class ErrorResponseTest {

    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `deserializa el JSON real de error del backend`() {
        val cuerpo = """
            {
              "timestamp": "2026-09-04T10:15:30",
              "status": 422,
              "error": "Unprocessable Entity",
              "mensaje": "El campo litros debe ser mayor a cero"
            }
        """.trimIndent()

        val resultado = json.decodeFromString<ErrorResponse>(cuerpo)

        assertEquals("2026-09-04T10:15:30", resultado.timestamp)
        assertEquals(422, resultado.status)
        assertEquals("Unprocessable Entity", resultado.error)
        assertEquals("El campo litros debe ser mayor a cero", resultado.mensaje)
    }
}
