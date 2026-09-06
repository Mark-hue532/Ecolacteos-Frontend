package com.ecolacteos.acopio.presentation

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Test 7 de `PROMPT_FASE_07.md §9`: escala exacta por campo (`§10.1`), `null` nunca formateado como `0`. */
class FormateadoresTest {

    @Test
    fun `fecha se formatea dd MM yyyy`() {
        assertEquals("04/09/2026", LocalDate(2026, 9, 4).formateada())
    }

    @Test
    fun `fecha y hora se formatea dd MM yyyy HH mm tal cual llega`() {
        assertEquals("04/09/2026 16:20", LocalDateTime(2026, 9, 4, 16, 20, 0).formateada())
    }

    @Test
    fun `precioUnitario y total de Venta usan escala 2`() {
        assertEquals("18.00", Decimal.parseString("18").formateadoConEscala(2))
        assertEquals("18.50", Decimal.parseString("18.5").formateadoConEscala(2))
        assertEquals("18.57", Decimal.parseString("18.567").formateadoConEscala(2))
    }

    @Test
    fun `un Decimal nulo nunca se formatea como 0`() {
        val nulo: Decimal? = null
        assertNull(nulo.formateadoConEscala(2))
    }
}
