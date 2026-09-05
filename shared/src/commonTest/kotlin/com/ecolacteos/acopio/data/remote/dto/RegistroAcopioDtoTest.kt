package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.aTextoConEscala
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegistroAcopioDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `RegistroAcopioResponse con campos nullable presentes`() {
        val texto = """
            {
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "uuidCliente": "aaa-bbb-ccc",
              "proveedorId": "p-1",
              "proveedorNombre": "Fundo Los Andes",
              "unidadId": "u-1",
              "fechaHora": "2026-09-04T06:30:00",
              "litros": 125.50,
              "gpsLat": -12.046374,
              "gpsLng": -77.042793,
              "motivoObservacion": "Agua añadida detectada",
              "litrosPorVoz": true,
              "sincronizadoEn": "2026-09-04T11:00:00"
            }
        """.trimIndent()
        val resultado = json.decodeFromString(RegistroAcopioResponse.serializer(), texto)

        assertEquals("125.50", resultado.litros.aTextoConEscala(2))
        assertEquals("-12.046374", resultado.gpsLat!!.aTextoConEscala(6))
        assertEquals("Agua añadida detectada", resultado.motivoObservacion)
        assertEquals(true, resultado.litrosPorVoz)
    }

    @Test
    fun `RegistroAcopioResponse con campos nullable ausentes`() {
        val texto = """
            {
              "id": "id-1", "uuidCliente": "uc-1", "proveedorId": "p-1", "proveedorNombre": "Fundo Los Andes",
              "unidadId": "u-1", "fechaHora": "2026-09-04T06:30:00", "litros": 100.00,
              "litrosPorVoz": false, "sincronizadoEn": "2026-09-04T11:00:00"
            }
        """.trimIndent()
        val resultado = json.decodeFromString(RegistroAcopioResponse.serializer(), texto)

        assertNull(resultado.gpsLat)
        assertNull(resultado.gpsLng)
        assertNull(resultado.motivoObservacion)
    }

    @Test
    fun `RegistroAcopioResumenResponse no tiene uuidCliente -- DATA-013`() {
        val texto = """{"id":"id-1","fechaHora":"2026-09-04T06:30:00","litros":50.00,"tieneObservacion":false}"""
        val resultado = json.decodeFromString(RegistroAcopioResumenResponse.serializer(), texto)

        assertEquals("50.00", resultado.litros.aTextoConEscala(2))
        assertEquals(false, resultado.tieneObservacion)
    }

    @Test
    fun `RegistroAcopioDTO Request serializa motivoObservacionId -- no motivoObservacion`() {
        val request = RegistroAcopioDTO(
            uuidCliente = "uc-1",
            proveedorId = "p-1",
            unidadId = "u-1",
            fechaHora = kotlinx.datetime.LocalDateTime(2026, 9, 4, 6, 30, 0),
            litros = com.ecolacteos.acopio.core.decimalDesdeTexto("100.00"),
            motivoObservacionId = "mo-1",
        )
        val salida = json.encodeToString(RegistroAcopioDTO.serializer(), request)

        assertEquals(true, salida.contains(""""motivoObservacionId":"mo-1""""))
    }

    @Test
    fun `CorreccionRegistroResponse deserializa con motivo presente y ausente`() {
        val conMotivo = json.decodeFromString(
            CorreccionRegistroResponse.serializer(),
            """{"id":"c-1","registroAcopioId":"r-1","litrosAnterior":100.00,"litrosCorregido":95.00,
                "motivo":"Balanza descalibrada","usuarioNombre":"Ana","creadoEn":"2026-09-04T12:00:00"}""",
        )
        assertEquals("Balanza descalibrada", conMotivo.motivo)

        val sinMotivo = json.decodeFromString(
            CorreccionRegistroResponse.serializer(),
            """{"id":"c-1","registroAcopioId":"r-1","litrosAnterior":100.00,"litrosCorregido":95.00,
                "usuarioNombre":"Ana","creadoEn":"2026-09-04T12:00:00"}""",
        )
        assertNull(sinMotivo.motivo)
    }
}
