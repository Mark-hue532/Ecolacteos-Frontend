package com.ecolacteos.acopio.data.remote.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ComunicadoConfirmacionDtoTest {

    private val json = Json

    @Test
    fun `ComunicadoConfirmacionResponse deserializa -- acopiador resuelto del JWT`() {
        val texto = """
            {"id":"cc-1","proveedorId":"p-1","proveedorNombre":"Fundo Los Andes",
             "acopiadorId":"a-1","acopiadorNombre":"Juana Quispe","confirmadoEn":"2026-09-04T07:00:00"}
        """.trimIndent()
        val resultado = json.decodeFromString(ComunicadoConfirmacionResponse.serializer(), texto)

        assertEquals("Juana Quispe", resultado.acopiadorNombre)
    }

    @Test
    fun `ConfirmarComunicadoRequest solo lleva proveedorId`() {
        val salida = json.encodeToString(ConfirmarComunicadoRequest.serializer(), ConfirmarComunicadoRequest("p-1"))
        assertEquals("""{"proveedorId":"p-1"}""", salida)
    }
}
