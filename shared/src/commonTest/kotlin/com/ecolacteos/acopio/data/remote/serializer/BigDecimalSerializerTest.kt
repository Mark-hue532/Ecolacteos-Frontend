package com.ecolacteos.acopio.data.remote.serializer

import com.ecolacteos.acopio.core.Decimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * El test más importante de la fase (`PROMPT_FASE_02.md §1.a`, `DATA-002`). Ningún caso de este archivo
 * pasa por `Double` -- ni siquiera para construir el JSON de entrada, que es siempre un literal de texto.
 *
 * `Decimal.parseString` (bignum) **no** conserva los ceros finales del literal por su cuenta -- normaliza a
 * dígitos significativos (`scale == -1` internamente). Por eso el serializer real de la app no es un
 * `object` único sino uno por escala fija ([BigDecimalEscala2Serializer], etc.), que reaplica la escala de
 * la columna Postgres real al formatear -- ver el comentario de `BigDecimalConEscalaSerializer`. Estos
 * tests usan la escala 2 (la más común del contrato) salvo que se indique lo contrario.
 */
class BigDecimalSerializerTest {

    @Serializable
    private data class Envoltorio(
        @Serializable(with = BigDecimalEscala2Serializer::class) val valor: Decimal,
    )

    @Serializable
    private data class EnvoltorioEscala6(
        @Serializable(with = BigDecimalEscala6Serializer::class) val valor: Decimal,
    )

    private val json = Json { isLenient = false }

    @Test
    fun `12_50 deserializa y vuelve a serializar como 12_50 -- no 12_5`() {
        val envoltorio = json.decodeFromString(Envoltorio.serializer(), """{"valor":12.50}""")

        val salida = json.encodeToString(Envoltorio.serializer(), envoltorio)
        assertEquals("""{"valor":12.50}""", salida)
    }

    @Test
    fun `un decimal grande no se convierte a notacion cientifica`() {
        val texto = """{"valor":99999999.99}"""
        val envoltorio = json.decodeFromString(Envoltorio.serializer(), texto)

        val salida = json.encodeToString(Envoltorio.serializer(), envoltorio)
        assertFalse(salida.contains("E", ignoreCase = true), "no debe usar notación científica: $salida")
        assertEquals(texto, salida)
    }

    @Test
    fun `gpsLat con 6 decimales preserva los 6 en escala 6`() {
        val envoltorio = json.decodeFromString(EnvoltorioEscala6.serializer(), """{"valor":-12.046374}""")

        val salida = json.encodeToString(EnvoltorioEscala6.serializer(), envoltorio)
        assertEquals("""{"valor":-12.046374}""", salida)
    }

    @Test
    fun `el JSON emitido nunca lleva comillas -- es number no string`() {
        val envoltorio = Envoltorio(Decimal.parseString("18.50"))
        val salida = json.encodeToString(Envoltorio.serializer(), envoltorio)
        assertEquals("""{"valor":18.50}""", salida)
    }

    @Test
    fun `0_1 mas 0_2 deserializados dan exactamente 0_3 -- no lo que daria Double`() {
        val a = json.decodeFromString(Envoltorio.serializer(), """{"valor":0.1}""").valor
        val b = json.decodeFromString(Envoltorio.serializer(), """{"valor":0.2}""").valor
        val suma = a + b
        // 0.1 + 0.2 en Double da 0.30000000000000004 -- acá tiene que dar exactamente "0.30".
        val salida = json.encodeToString(Envoltorio.serializer(), Envoltorio(suma))
        assertEquals("""{"valor":0.30}""", salida)
    }

    @Test
    fun `cero preserva la escala configurada al serializar`() {
        val envoltorio = json.decodeFromString(Envoltorio.serializer(), """{"valor":0.00}""")
        val salida = json.encodeToString(Envoltorio.serializer(), envoltorio)
        assertEquals("""{"valor":0.00}""", salida)
    }
}
