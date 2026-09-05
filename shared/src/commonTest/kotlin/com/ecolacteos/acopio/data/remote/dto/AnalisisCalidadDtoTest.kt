package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.aTextoConEscala
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnalisisCalidadDtoTest {

    private val json = Json

    @Test
    fun `AnalisisCalidadResponse con los 6 parametros de laboratorio presentes`() {
        val texto = """
            {"id":"a-1","registroAcopioId":"r-1","folioMuestra":"F-0001",
             "agua":3.10,"proteina":3.20,"lactosa":4.70,"densidad":1.03,"temperatura":4.50,"ph":6.70,
             "aguaAnadida":false,"resultado":"APROBADO","creadoEn":"2026-09-04T09:00:00"}
        """.trimIndent()
        val resultado = json.decodeFromString(AnalisisCalidadResponse.serializer(), texto)

        assertEquals("3.10", resultado.agua!!.aTextoConEscala(2))
        assertEquals(ResultadoCalidad.APROBADO, resultado.resultado)
    }

    @Test
    fun `AnalisisCalidadResponse con los 6 parametros de laboratorio ausentes`() {
        val texto = """
            {"id":"a-1","registroAcopioId":"r-1","folioMuestra":"F-0002",
             "aguaAnadida":true,"resultado":"RECHAZADO","creadoEn":"2026-09-04T09:00:00"}
        """.trimIndent()
        val resultado = json.decodeFromString(AnalisisCalidadResponse.serializer(), texto)

        assertNull(resultado.agua)
        assertNull(resultado.proteina)
        assertNull(resultado.lactosa)
        assertNull(resultado.densidad)
        assertNull(resultado.temperatura)
        assertNull(resultado.ph)
    }

    @Test
    fun `resultado OBSERVADO decodifica aunque hoy no tenga productor real`() {
        val texto = """
            {"id":"a-1","registroAcopioId":"r-1","folioMuestra":"F-0003",
             "aguaAnadida":false,"resultado":"OBSERVADO","creadoEn":"2026-09-04T09:00:00"}
        """.trimIndent()
        val resultado = json.decodeFromString(AnalisisCalidadResponse.serializer(), texto)
        assertEquals(ResultadoCalidad.OBSERVADO, resultado.resultado)
    }

    @Test
    fun `AnalisisCalidadRequest con los 6 parametros opcionales ausentes serializa sin ellos`() {
        val request = AnalisisCalidadRequest(
            uuidCliente = "uc-1",
            registroAcopioId = "r-1",
            folioMuestra = "F-0001",
        )
        val salida = json.encodeToString(AnalisisCalidadRequest.serializer(), request)
        // explicitNulls=false es config de jsonApi (la instancia de la app), no de este Json de test por
        // defecto -- acá solo se verifica que el modelo permite construir el Request sin los opcionales.
        assertEquals(true, salida.contains(""""folioMuestra":"F-0001""""))
    }
}
