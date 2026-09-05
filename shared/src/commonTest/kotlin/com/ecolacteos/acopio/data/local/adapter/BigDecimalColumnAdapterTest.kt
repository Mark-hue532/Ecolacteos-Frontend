package com.ecolacteos.acopio.data.local.adapter

import com.ecolacteos.acopio.core.Decimal
import kotlin.test.Test
import kotlin.test.assertEquals

class BigDecimalColumnAdapterTest {

    @Test
    fun `escala 2 -- roundtrip preserva la escala exacta -- 12_50 no se vuelve 12_5`() {
        val original = Decimal.parseString("12.50")
        val texto = BigDecimalEscala2ColumnAdapter.encode(original)

        // bignum no conserva los ceros finales del literal por si solo (toStringExpanded() daria "12.5") --
        // por eso el adapter aplica aTextoConEscala(2), igual que BigDecimalEscala2Serializer de Fase 2.
        assertEquals("12.50", texto)
        assertEquals(original, BigDecimalEscala2ColumnAdapter.decode(texto))
    }

    @Test
    fun `escala 2 -- nunca usa notacion cientifica`() {
        val grande = Decimal.parseString("1234567.89")
        assertEquals("1234567.89", BigDecimalEscala2ColumnAdapter.encode(grande))
    }

    @Test
    fun `escala 6 -- roundtrip de coordenadas GPS preserva los 6 decimales`() {
        val gps = Decimal.parseString("-12.045678")
        val texto = BigDecimalEscala6ColumnAdapter.encode(gps)

        assertEquals("-12.045678", texto)
        assertEquals(gps, BigDecimalEscala6ColumnAdapter.decode(texto))
    }

    @Test
    fun `escala 6 -- un valor con menos decimales se rellena con ceros`() {
        val gps = Decimal.parseString("-12.04")
        assertEquals("-12.040000", BigDecimalEscala6ColumnAdapter.encode(gps))
    }
}
