package com.ecolacteos.acopio.data.remote.serializer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Los cuatro tipos de fecha del contrato (`MOBILE_DATA_MAPPING.md §1.4`), no intercambiables. Ninguna
 * función de este archivo convierte `LocalDateTime` a `Instant` (`CLAUDE.md §3.2`, `DATA-001`).
 */
class FechaSerializersTest {

    private val json = Json

    @Serializable
    private data class ConFecha(@Serializable(with = LocalDateSerializer::class) val valor: LocalDate)

    @Serializable
    private data class ConFechaHora(
        @Serializable(with = LocalDateTimeSerializer::class) val valor: LocalDateTime,
    )

    @Serializable
    private data class ConHora(@Serializable(with = LocalTimeSerializer::class) val valor: LocalTime)

    @Serializable
    private data class ConInstante(@Serializable(with = InstantSerializer::class) val valor: Instant)

    @Test
    fun `LocalDate parsea yyyy-MM-dd`() {
        val resultado = json.decodeFromString(ConFecha.serializer(), """{"valor":"2026-09-04"}""")
        assertEquals(LocalDate(2026, 9, 4), resultado.valor)
    }

    @Test
    fun `LocalDateTime parsea sin fraccion de segundo`() {
        val resultado = json.decodeFromString(ConFechaHora.serializer(), """{"valor":"2026-09-04T10:15:30"}""")
        assertEquals(LocalDateTime(2026, 9, 4, 10, 15, 30), resultado.valor)
    }

    @Test
    fun `LocalDateTime parsea con fraccion de segundo`() {
        val resultado = json.decodeFromString(
            ConFechaHora.serializer(),
            """{"valor":"2026-09-04T10:15:30.123456"}""",
        )
        assertEquals(2026, resultado.valor.year)
        assertEquals(30, resultado.valor.second)
        assertEquals(123_456_000, resultado.valor.nanosecond)
    }

    @Test
    fun `LocalDateTime serializado no lleva offset ni Z`() {
        val original = ConFechaHora(LocalDateTime(2026, 9, 4, 10, 15, 30))
        val salida = json.encodeToString(ConFechaHora.serializer(), original)
        assertEquals("""{"valor":"2026-09-04T10:15:30"}""", salida)
    }

    @Test
    fun `LocalDateTime con segundos en cero serializa con segundos explicitos, no los omite`() {
        // value.toString() de kotlinx-datetime emitiria "2026-09-04T06:00" -- sin ":00". Ver DATA-001.
        val original = ConFechaHora(LocalDateTime(2026, 9, 4, 6, 0, 0))
        val salida = json.encodeToString(ConFechaHora.serializer(), original)
        assertEquals("""{"valor":"2026-09-04T06:00:00"}""", salida)
    }

    @Test
    fun `LocalDateTime con fraccion de segundo serializa la fraccion`() {
        val original = ConFechaHora(LocalDateTime(2026, 9, 4, 10, 15, 30, 123_456_000))
        val salida = json.encodeToString(ConFechaHora.serializer(), original)
        assertEquals("""{"valor":"2026-09-04T10:15:30.123456"}""", salida)
    }

    @Test
    fun `LocalDateTime deserializa las tres formas al mismo valor`() {
        val esperado = LocalDateTime(2026, 9, 4, 6, 0, 0)
        val sinSegundos = json.decodeFromString(ConFechaHora.serializer(), """{"valor":"2026-09-04T06:00"}""")
        val conSegundos = json.decodeFromString(ConFechaHora.serializer(), """{"valor":"2026-09-04T06:00:00"}""")
        assertEquals(esperado, sinSegundos.valor)
        assertEquals(esperado, conSegundos.valor)

        val esperadoConFraccion = LocalDateTime(2026, 9, 4, 6, 0, 0, 123_456_000)
        val conFraccion = json.decodeFromString(
            ConFechaHora.serializer(),
            """{"valor":"2026-09-04T06:00:00.123456"}""",
        )
        assertEquals(esperadoConFraccion, conFraccion.valor)
    }

    @Test
    fun `LocalTime parsea HH-mm-ss`() {
        val resultado = json.decodeFromString(ConHora.serializer(), """{"valor":"14:30:00"}""")
        assertEquals(LocalTime(14, 30, 0), resultado.valor)
    }

    @Test
    fun `LocalTime con segundos en cero serializa con segundos explicitos, no los omite`() {
        // Mismo bug que LocalDateTime: LocalTime.toString() emitiria "14:30", sin ":00".
        val original = ConHora(LocalTime(14, 30, 0))
        val salida = json.encodeToString(ConHora.serializer(), original)
        assertEquals("""{"valor":"14:30:00"}""", salida)
    }

    @Test
    fun `LocalTime deserializa las tres formas al mismo valor`() {
        val esperado = LocalTime(14, 30, 0)
        val sinSegundos = json.decodeFromString(ConHora.serializer(), """{"valor":"14:30"}""")
        val conSegundos = json.decodeFromString(ConHora.serializer(), """{"valor":"14:30:00"}""")
        assertEquals(esperado, sinSegundos.valor)
        assertEquals(esperado, conSegundos.valor)

        val esperadoConFraccion = LocalTime(14, 30, 0, 123_456_000)
        val conFraccion = json.decodeFromString(ConHora.serializer(), """{"valor":"14:30:00.123456"}""")
        assertEquals(esperadoConFraccion, conFraccion.valor)
    }

    @Test
    fun `Instant con Z parsea como UTC`() {
        val resultado = json.decodeFromString(
            ConInstante.serializer(),
            """{"valor":"2026-09-04T15:30:00.123456Z"}""",
        )
        assertEquals("2026-09-04T15:30:00.123456Z", resultado.valor.toString())
    }
}
