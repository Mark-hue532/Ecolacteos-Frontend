package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.aTextoConEscala
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class VentaDtoTest {

    private val json = Json

    @Test
    fun `VentaResponse deserializa con total generado por Postgres`() {
        val texto = """
            {"id":"v-1","fecha":"2026-09-04","tipoCliente":"MAYORISTA","tipoQuesoNombre":"Fresco",
             "cantidad":10,"precioUnitario":15.50,"total":155.00}
        """.trimIndent()
        val resultado = json.decodeFromString(VentaResponse.serializer(), texto)

        assertEquals(TipoClienteVenta.MAYORISTA, resultado.tipoCliente)
        assertEquals("155.00", resultado.total.aTextoConEscala(2))
    }

    @Test
    fun `VentaRequest serializa tipoCliente como uno de los 3 valores del enum`() {
        val request = VentaRequest(
            uuidCliente = "uc-1",
            fecha = kotlinx.datetime.LocalDate(2026, 9, 4),
            tipoCliente = TipoClienteVenta.PUBLICO,
            tipoQuesoId = "tq-1",
            cantidad = 5,
            precioUnitario = com.ecolacteos.acopio.core.decimalDesdeTexto("20.00"),
        )
        val salida = json.encodeToString(VentaRequest.serializer(), request)
        assertEquals(true, salida.contains(""""tipoCliente":"PUBLICO""""))
    }

    @Test
    fun `un tipoCliente desconocido en la respuesta decodifica a UNKNOWN sin romper`() {
        val texto = """
            {"id":"v-2","fecha":"2026-09-04","tipoCliente":"OTRO","tipoQuesoNombre":"Fresco",
             "cantidad":1,"precioUnitario":10.00,"total":10.00}
        """.trimIndent()
        val resultado = json.decodeFromString(VentaResponse.serializer(), texto)
        assertEquals(TipoClienteVenta.UNKNOWN, resultado.tipoCliente)
    }
}
