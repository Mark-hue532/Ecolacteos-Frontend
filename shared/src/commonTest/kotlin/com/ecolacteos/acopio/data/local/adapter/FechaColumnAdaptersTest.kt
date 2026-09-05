package com.ecolacteos.acopio.data.local.adapter

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FechaColumnAdaptersTest {

    @Test
    fun `LocalDateTime con segundos en cero se codifica con segundos explicitos`() {
        val fecha = LocalDateTime(2026, 9, 4, 6, 0, 0)

        // Trampa #5 de PROMPT_FASE_04.md: value.toString() da "2026-09-04T06:00", sin segundos.
        assertEquals("2026-09-04T06:00:00", LocalDateTimeColumnAdapter.encode(fecha))
    }

    @Test
    fun `LocalDateTime roundtrip preserva fraccion de segundo`() {
        val fecha = LocalDateTime(2026, 9, 4, 10, 15, 30, 123_000_000)
        val texto = LocalDateTimeColumnAdapter.encode(fecha)

        assertEquals("2026-09-04T10:15:30.123", texto)
        assertEquals(fecha, LocalDateTimeColumnAdapter.decode(texto))
    }

    @Test
    fun `LocalDate roundtrip`() {
        val fecha = LocalDate(2026, 9, 4)
        val texto = LocalDateColumnAdapter.encode(fecha)

        assertEquals("2026-09-04", texto)
        assertEquals(fecha, LocalDateColumnAdapter.decode(texto))
    }

    @Test
    fun `LocalTime con segundos en cero tambien se codifica explicito`() {
        val hora = LocalTime(14, 30, 0)
        assertEquals("14:30:00", LocalTimeColumnAdapter.encode(hora))
        assertEquals(hora, LocalTimeColumnAdapter.decode("14:30:00"))
    }
}
