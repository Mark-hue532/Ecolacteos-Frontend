package com.ecolacteos.acopio.data.remote.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginDtoTest {

    private val json = Json

    @Test
    fun `LoginResponse deserializa el JSON real del backend`() {
        val texto = """
            {"token":"eyJhbGciOiJIUzI1NiJ9.abc.def","rol":"ACOPIADOR","nombre":"Juana Quispe","expiraEnSegundos":28800}
        """.trimIndent()
        val resultado = json.decodeFromString(LoginResponse.serializer(), texto)

        assertEquals("eyJhbGciOiJIUzI1NiJ9.abc.def", resultado.token)
        assertEquals(Rol.ACOPIADOR, resultado.rol)
        assertEquals("Juana Quispe", resultado.nombre)
        assertEquals(28_800L, resultado.expiraEnSegundos)
    }

    @Test
    fun `LoginRequest serializa email y password sin campos extra`() {
        val salida = json.encodeToString(LoginRequest.serializer(), LoginRequest("a@b.com", "secreta"))
        assertEquals("""{"email":"a@b.com","password":"secreta"}""", salida)
    }
}
