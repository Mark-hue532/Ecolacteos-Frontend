package com.ecolacteos.acopio.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UuidTest {

    @Test
    fun `genera un UUID v4 con formato RFC 4122 valido`() {
        repeat(50) {
            val uuid = generarUuidV4()
            assertTrue(esUuidV4Valido(uuid), "'$uuid' no es un UUID v4 valido")
        }
    }

    @Test
    fun `dos generaciones sucesivas son distintas`() {
        val generados = List(200) { generarUuidV4() }
        assertEquals(generados.size, generados.toSet().size, "se generaron UUIDs repetidos")
    }

    @Test
    fun `esUuidV4Valido rechaza formatos incorrectos`() {
        assertTrue(!esUuidV4Valido(""))
        assertTrue(!esUuidV4Valido("550e8400-e29b-41d4-a716-44665544000")) // corto
        assertTrue(!esUuidV4Valido("550e8400-e29b-11d4-a716-446655440000")) // version 1, no 4
        assertTrue(!esUuidV4Valido("550E8400-E29B-41D4-A716-446655440000")) // mayusculas
    }

    @Test
    fun `es determinista con un Random fijo -- prueba de forma no de valor`() {
        val uuid = generarUuidV4(Random(42))
        assertTrue(esUuidV4Valido(uuid))
    }
}
