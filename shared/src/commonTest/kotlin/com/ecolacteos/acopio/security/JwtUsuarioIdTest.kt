package com.ecolacteos.acopio.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `usuarioIdDesdeJwt` (`PROMPT_FASE_03.md §4` y `§8`). Los tokens de prueba son JWT reales en su forma
 * (header.payload.firma), la firma es un string cualquiera -- a propósito, nunca se verifica.
 */
class JwtUsuarioIdTest {

    // header {"alg":"HS256","typ":"JWT"}, payload {"sub":"ana@ecolacteos.pe","rol":"ACOPIADOR","usuarioId":"u-123"}
    private val tokenValido =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJzdWIiOiJhbmFAZWNvbGFjdGVvcy5wZSIsInJvbCI6IkFDT1BJQURPUiIsInVzdWFyaW9JZCI6InUtMTIzIn0" +
            ".firma-no-verificada"

    // mismo header, payload sin el claim usuarioId
    private val tokenSinClaim =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJzdWIiOiJhbmFAZWNvbGFjdGVvcy5wZSIsInJvbCI6IkFDT1BJQURPUiJ9" +
            ".firma-no-verificada"

    // payload {"sub":"a@b.com","rol":"ACOPIADOR","usuarioId":"u-001???>>>"} -- fuerza '-' Y '_' en el
    // base64url (donde base64 estandar tendria '+'/'/') y longitud sin multiplo de 4 (sin padding).
    private val tokenConGuionYGuionBajo =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJzdWIiOiJhQGIuY29tIiwicm9sIjoiQUNPUElBRE9SIiwidXN1YXJpb0lkIjoidS0wMDE_Pz8-Pj4ifQ" +
            ".firma-no-verificada"

    @Test
    fun `extrae usuarioId de un token valido`() {
        assertEquals("u-123", usuarioIdDesdeJwt(tokenValido))
    }

    @Test
    fun `base64url con guion y guion bajo -- y sin padding -- decodifica bien`() {
        // Es el caso que rompe si se usara base64 estandar en vez de la variante URL (PROMPT_FASE_03.md §8).
        assertEquals("u-001???>>>", usuarioIdDesdeJwt(tokenConGuionYGuionBajo))
    }

    @Test
    fun `payload sin el claim usuarioId devuelve null sin lanzar`() {
        assertNull(usuarioIdDesdeJwt(tokenSinClaim))
    }

    @Test
    fun `token con dos segmentos devuelve null sin lanzar`() {
        assertNull(usuarioIdDesdeJwt("solo.dossegmentos"))
    }

    @Test
    fun `token con cuatro segmentos devuelve null sin lanzar`() {
        assertNull(usuarioIdDesdeJwt("a.b.c.d"))
    }

    @Test
    fun `token vacio devuelve null sin lanzar`() {
        assertNull(usuarioIdDesdeJwt(""))
    }

    @Test
    fun `payload con caracteres fuera del alfabeto base64url devuelve null sin lanzar`() {
        assertNull(usuarioIdDesdeJwt("header.payload con espacios!.firma"))
    }

    @Test
    fun `payload que decodifica a texto que no es JSON devuelve null sin lanzar`() {
        // "no-es-json" en base64url, sin comillas ni llaves -- decodifica a bytes validos pero no a JSON.
        val payloadNoJson = codificarBase64UrlParaTest("no-es-json")
        assertNull(usuarioIdDesdeJwt("header.$payloadNoJson.firma"))
    }

    @Test
    fun `payload que es un array JSON en vez de un objeto devuelve null sin lanzar`() {
        val payloadArray = codificarBase64UrlParaTest("""["no","es","un","objeto"]""")
        assertNull(usuarioIdDesdeJwt("header.$payloadArray.firma"))
    }
}

/** Codifica a base64url -- inverso de [decodificarBase64Url], solo para armar fixtures de este test. */
private fun codificarBase64UrlParaTest(texto: String): String {
    val alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val bytes = texto.encodeToByteArray()
    val salida = StringBuilder()
    var buffer = 0
    var bitsEnBuffer = 0
    for (byte in bytes) {
        buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
        bitsEnBuffer += 8
        while (bitsEnBuffer >= 6) {
            bitsEnBuffer -= 6
            salida.append(alfabeto[(buffer shr bitsEnBuffer) and 0x3F])
        }
    }
    if (bitsEnBuffer > 0) {
        salida.append(alfabeto[(buffer shl (6 - bitsEnBuffer)) and 0x3F])
    }
    return salida.toString()
}
