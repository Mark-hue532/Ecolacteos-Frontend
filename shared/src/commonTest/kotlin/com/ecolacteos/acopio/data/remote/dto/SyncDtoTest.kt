package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.aTextoConEscala
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SyncResultResponse deserializa confirmados y errores`() {
        val texto = """
            {"confirmados":["uc-1","uc-2"],
             "errores":[{"uuidCliente":"uc-3","motivo":"proveedorId no existe"}]}
        """.trimIndent()
        val resultado = json.decodeFromString(SyncResultResponse.serializer(), texto)

        assertEquals(listOf("uc-1", "uc-2"), resultado.confirmados)
        assertEquals(1, resultado.errores.size)
        assertEquals("proveedorId no existe", resultado.errores.first().motivo)
    }

    @Test
    fun `SyncResultResponse con listas vacias`() {
        val resultado = json.decodeFromString(SyncResultResponse.serializer(), """{"confirmados":[],"errores":[]}""")
        assertEquals(emptyList(), resultado.confirmados)
        assertEquals(emptyList(), resultado.errores)
    }

    @Test
    fun `CambiosResponse completo -- con precioLitroVigente presente`() {
        val texto = """
            {
              "generadoEn": "2026-09-04T15:30:00.123456Z",
              "proveedores": [
                {"id":"p-1","nombre":"Fundo Los Andes","zonaActualId":"z-1","zonaActualNombre":"Zona Norte","codigoQr":"qr-1"}
              ],
              "precioLitroVigente": 1.85,
              "comunicados": [
                {"id":"c-1","mensaje":"Corte de ruta","fecha":"2026-09-04T08:00:00","zonasNombres":["Zona Norte"]}
              ],
              "prediccionesProveedor": [
                {"proveedorId":"p-1","fechaPrevista":"2026-09-05","litrosEstimadosMin":80.00,"litrosEstimadosMax":120.00}
              ],
              "motivosObservacion": [ {"id":"mo-1","descripcion":"Agua añadida"} ],
              "tiposQueso": [
                {"id":"tq-1","nombre":"Fresco","rendimientoEsperadoPct":12.00,"cicloCapital":"RAPIDO","activo":true}
              ],
              "unidades": [
                {"id":"u-1","placa":"ABC-123","capacidadTon":5.50,"zonaId":"z-1","responsableId":"resp-1","responsableNombre":"Luis"}
              ]
            }
        """.trimIndent()
        val resultado = json.decodeFromString(CambiosResponse.serializer(), texto)

        assertEquals("2026-09-04T15:30:00.123456Z", resultado.generadoEn.toString())
        assertEquals("1.85", resultado.precioLitroVigente!!.aTextoConEscala(2))
        // fecha de ComunicadoResponse es LocalDateTime pese al nombre -- no debe fallar ni truncarse a LocalDate.
        assertEquals(kotlinx.datetime.LocalDateTime(2026, 9, 4, 8, 0, 0), resultado.comunicados.first().fecha)
        assertEquals(1, resultado.proveedores.size)
        assertEquals(1, resultado.tiposQueso.size)
        assertEquals(1, resultado.unidades.size)
    }

    @Test
    fun `CambiosResponse con precioLitroVigente ausente -- sin precio configurado`() {
        val texto = """
            {"generadoEn":"2026-09-04T15:30:00Z","proveedores":[],"comunicados":[],
             "prediccionesProveedor":[],"motivosObservacion":[],"tiposQueso":[],"unidades":[]}
        """.trimIndent()
        val resultado = json.decodeFromString(CambiosResponse.serializer(), texto)
        assertNull(resultado.precioLitroVigente)
    }

    @Test
    fun `ProveedorPublicoResponse con zona y qr ausentes`() {
        val texto = """{"id":"p-2","nombre":"Fundo Sin Zona"}"""
        val resultado = json.decodeFromString(ProveedorPublicoResponse.serializer(), texto)
        assertNull(resultado.zonaActualId)
        assertNull(resultado.zonaActualNombre)
        assertNull(resultado.codigoQr)
    }

    @Test
    fun `UnidadResponse con capacidadTon y zonaId ausentes`() {
        val texto = """{"id":"u-2","placa":"XYZ-999","responsableId":"resp-2","responsableNombre":"Ana"}"""
        val resultado = json.decodeFromString(UnidadResponse.serializer(), texto)
        assertNull(resultado.capacidadTon)
        assertNull(resultado.zonaId)
    }

    @Test
    fun `un campo desconocido en la respuesta no rompe la deserializacion`() {
        val texto = """{"id":"mo-1","descripcion":"Agua añadida","campoQueElBackendAgregoManana":true}"""
        val resultado = json.decodeFromString(MotivoObservacionResponse.serializer(), texto)
        assertEquals("Agua añadida", resultado.descripcion)
    }
}
