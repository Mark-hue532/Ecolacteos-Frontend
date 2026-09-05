package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.aTextoConEscala
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PagoResponseTest {

    private val json = Json

    @Test
    fun `PagoResponse preserva los 3 decimales de precioLitro`() {
        val texto = """
            {"id":"pg-1","proveedorId":"p-1","proveedorNombre":"Fundo Los Andes",
             "semanaInicio":"2026-09-01","semanaFin":"2026-09-07","litrosTotales":1000.00,
             "precioLitro":1.850,"total":1850.00,"comprobanteGenerado":false,"registroAcopioIds":["r-1","r-2"]}
        """.trimIndent()
        val resultado = json.decodeFromString(PagoResponse.serializer(), texto)

        // precioLitro tiene scale=3, no 2 -- si se truncara a 2 decimales seria un bug silencioso.
        assertEquals("1.850", resultado.precioLitro.aTextoConEscala(3))
        assertEquals("1850.00", resultado.total.aTextoConEscala(2))
    }
}
