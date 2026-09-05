package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.aTextoConEscala
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InnovacionDtoTest {

    private val json = Json

    @Test
    fun `ScoreConfianzaResponse deserializa los 4 componentes`() {
        val texto = """
            {"proveedorId":"p-1","periodo":"2026-09-01","score":85.50,
             "componenteCalidad":30.00,"componenteRegularidad":30.00,"componenteAnomalias":25.50}
        """.trimIndent()
        val resultado = json.decodeFromString(ScoreConfianzaResponse.serializer(), texto)
        assertEquals("85.50", resultado.score.aTextoConEscala(2))
    }

    @Test
    fun `AlertaAnomaliaResponse con zScore presente`() {
        val texto = """
            {"id":"al-1","registroAcopioId":"r-1","proveedorId":"p-1","proveedorNombre":"Fundo Los Andes",
             "tipo":"VOLUMEN_ATIPICO","zScore":3.200,"severidad":"ALTA","creadoEn":"2026-09-04T05:00:00"}
        """.trimIndent()
        val resultado = json.decodeFromString(AlertaAnomaliaResponse.serializer(), texto)

        assertEquals(TipoAlerta.VOLUMEN_ATIPICO, resultado.tipo)
        assertEquals(Severidad.ALTA, resultado.severidad)
        assertEquals("3.200", resultado.zScore!!.aTextoConEscala(3))
    }

    @Test
    fun `AlertaAnomaliaResponse con zScore ausente`() {
        val texto = """
            {"id":"al-2","registroAcopioId":"r-2","proveedorId":"p-2","proveedorNombre":"Fundo Sur",
             "tipo":"RIESGO_ADULTERACION","severidad":"MEDIA","creadoEn":"2026-09-04T05:00:00"}
        """.trimIndent()
        val resultado = json.decodeFromString(AlertaAnomaliaResponse.serializer(), texto)
        assertNull(resultado.zScore)
    }
}
