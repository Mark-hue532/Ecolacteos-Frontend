package com.ecolacteos.acopio.data.remote.dto

import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RutaProveedorOrdenResponseTest {

    private val json = Json

    @Test
    fun `con horaEstimada presente`() {
        val texto = """{"id":"ro-1","proveedorId":"p-1","proveedorNombre":"Fundo Los Andes","orden":1,"horaEstimada":"07:30:00"}"""
        val resultado = json.decodeFromString(RutaProveedorOrdenResponse.serializer(), texto)
        assertEquals(LocalTime(7, 30, 0), resultado.horaEstimada)
    }

    @Test
    fun `con horaEstimada ausente`() {
        val texto = """{"id":"ro-2","proveedorId":"p-2","proveedorNombre":"Fundo Sur","orden":2}"""
        val resultado = json.decodeFromString(RutaProveedorOrdenResponse.serializer(), texto)
        assertNull(resultado.horaEstimada)
    }
}
