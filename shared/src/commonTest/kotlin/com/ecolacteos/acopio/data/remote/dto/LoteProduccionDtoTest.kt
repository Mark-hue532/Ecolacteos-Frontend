package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.aTextoConEscala
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoteProduccionDtoTest {

    private val json = Json

    @Test
    fun `LoteProduccionResponse con rendimientoPct presente -- litrosUsados mayor a cero`() {
        val texto = """
            {"id":"l-1","fecha":"2026-09-04","tipoQuesoNombre":"Fresco","litrosUsados":100.00,
             "unidadesObtenidas":10,"rendimientoPct":11.50,"rendimientoEsperadoPct":12.00,
             "registroAcopioIds":["r-1","r-2"]}
        """.trimIndent()
        val resultado = json.decodeFromString(LoteProduccionResponse.serializer(), texto)

        assertEquals("11.50", resultado.rendimientoPct!!.aTextoConEscala(2))
        assertEquals(listOf("r-1", "r-2"), resultado.registroAcopioIds)
    }

    @Test
    fun `LoteProduccionResponse con rendimientoPct ausente -- litrosUsados igual a cero`() {
        val texto = """
            {"id":"l-2","fecha":"2026-09-04","tipoQuesoNombre":"Fresco","litrosUsados":0.00,
             "unidadesObtenidas":0,"rendimientoEsperadoPct":12.00,"registroAcopioIds":["r-3"]}
        """.trimIndent()
        val resultado = json.decodeFromString(LoteProduccionResponse.serializer(), texto)

        assertNull(resultado.rendimientoPct)
    }

    @Test
    fun `CrearLoteRequest serializa registroAcopioIds como array de string`() {
        val request = CrearLoteRequest(
            uuidCliente = "uc-1",
            fecha = kotlinx.datetime.LocalDate(2026, 9, 4),
            tipoQuesoId = "tq-1",
            litrosUsados = com.ecolacteos.acopio.core.decimalDesdeTexto("100.00"),
            unidadesObtenidas = 10,
            registroAcopioIds = listOf("r-1", "r-2"),
        )
        val salida = json.encodeToString(CrearLoteRequest.serializer(), request)
        assertEquals(true, salida.contains(""""registroAcopioIds":["r-1","r-2"]"""))
    }
}
