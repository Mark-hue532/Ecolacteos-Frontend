package com.ecolacteos.acopio.core

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class FechaUtilsTest {

    @Test
    fun `ahoraComoFechaHora devuelve la hora local del reloj y zona dados sin conversion`() {
        val instanteFijo = Instant.parse("2026-09-04T15:30:00Z")
        val relojFijo = object : Clock {
            override fun now() = instanteFijo
        }
        val zona = TimeZone.of("America/Lima") // UTC-5

        val resultado = ahoraComoFechaHora(relojFijo, zona)

        val esperado = instanteFijo.toLocalDateTime(zona)
        assertEquals(esperado, resultado)
        assertEquals(10, resultado.hour) // 15:30 UTC -> 10:30 America/Lima
    }

    @Test
    fun `aTextoIso8601 no lleva offset ni Z`() {
        val fecha = fechaHoraDesdeTexto("2026-09-04T10:15:30")
        val texto = fecha.aTextoIso8601()
        assertEquals("2026-09-04T10:15:30", texto)
    }

    @Test
    fun `roundtrip texto menos LocalDateTime menos texto es identico`() {
        val original = "2026-09-04T10:15:30.123456"
        assertEquals(original, fechaHoraDesdeTexto(original).aTextoIso8601())
    }
}
