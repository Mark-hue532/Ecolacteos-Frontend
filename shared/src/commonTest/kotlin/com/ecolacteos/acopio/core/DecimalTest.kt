package com.ecolacteos.acopio.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `"12.50"` debe seguir siendo `"12.50"` -- nunca `"12.5"` -- porque el sistema liquida pagos a proveedores
 * centavo a centavo contra columnas `GENERATED` de Postgres (CLAUDE.md §3.1, DATA-002). Ningún test de
 * este archivo pasa por `Double`.
 */
class DecimalTest {

    @Test
    fun `roundtrip String menos Decimal menos String preserva la escala exacta`() {
        assertEquals("12.50", decimalDesdeTexto("12.50").aTextoConEscala(2))
        assertEquals("0.00", decimalDesdeTexto("0").aTextoConEscala(2))
        assertEquals("1234.56", decimalDesdeTexto("1234.56").aTextoConEscala(2))
    }

    @Test
    fun `formatear con escala mayor rellena con ceros`() {
        assertEquals("12.500", decimalDesdeTexto("12.5").aTextoConEscala(3))
        assertEquals("7.000000", decimalDesdeTexto("7").aTextoConEscala(6))
    }

    @Test
    fun `formatear con escala menor redondea half up`() {
        assertEquals("12.57", decimalDesdeTexto("12.567").aTextoConEscala(2))
        assertEquals("12.56", decimalDesdeTexto("12.564").aTextoConEscala(2))
    }

    @Test
    fun `escala cero no deja punto decimal`() {
        assertEquals("13", decimalDesdeTexto("12.5").aTextoConEscala(0))
    }

    @Test
    fun `preserva el signo negativo`() {
        assertEquals("-12.50", decimalDesdeTexto("-12.5").aTextoConEscala(2))
    }

    @Test
    fun `la aritmetica no pierde precision como lo haria Double`() {
        // 0.1 + 0.2 en Double da 0.30000000000000004 -- en Decimal debe dar exactamente 0.3.
        val resultado = decimalDesdeTexto("0.1") + decimalDesdeTexto("0.2")
        assertEquals("0.30", resultado.aTextoConEscala(2))
    }
}
