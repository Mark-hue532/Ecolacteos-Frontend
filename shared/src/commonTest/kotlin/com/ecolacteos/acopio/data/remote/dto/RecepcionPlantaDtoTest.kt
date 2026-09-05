package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.aTextoConEscala
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecepcionPlantaDtoTest {

    private val json = Json

    @Test
    fun `RecepcionPlantaResponse con litrosRegistradosAcopio presente`() {
        val texto = """
            {"id":"rp-1","fecha":"2026-09-04","turno":"UNICO","unidadId":"u-1",
             "litrosCampo":500.00,"litrosPlanta":495.00,"diferenciaPct":-1.00,
             "estado":"ALERTA","litrosRegistradosAcopio":500.00}
        """.trimIndent()
        val resultado = json.decodeFromString(RecepcionPlantaResponse.serializer(), texto)

        assertEquals(EstadoConciliacion.ALERTA, resultado.estado)
        assertEquals("500.00", resultado.litrosRegistradosAcopio!!.aTextoConEscala(2))
    }

    @Test
    fun `RecepcionPlantaResponse con litrosRegistradosAcopio ausente -- SUM sobre 0 filas`() {
        val texto = """
            {"id":"rp-2","fecha":"2026-09-04","turno":"UNICO","unidadId":"u-2",
             "litrosCampo":300.00,"litrosPlanta":300.00,"diferenciaPct":0.00,"estado":"OK"}
        """.trimIndent()
        val resultado = json.decodeFromString(RecepcionPlantaResponse.serializer(), texto)
        assertNull(resultado.litrosRegistradosAcopio)
    }

    @Test
    fun `RecepcionPlantaRequest con turno ausente -- el server aplica el default`() {
        val request = RecepcionPlantaRequest(
            fecha = kotlinx.datetime.LocalDate(2026, 9, 4),
            unidadId = "u-1",
            litrosCampo = com.ecolacteos.acopio.core.decimalDesdeTexto("500.00"),
            litrosPlanta = com.ecolacteos.acopio.core.decimalDesdeTexto("495.00"),
        )
        assertNull(request.turno)
    }
}
