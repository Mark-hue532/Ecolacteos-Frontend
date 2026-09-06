package com.ecolacteos.acopio.synchronization

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Fija por test los dos números que `CLAUDE.md §7` deja a decisión del cliente, para que un cambio
 * accidental de la secuencia o del tamaño de fragmento no pase inadvertido.
 */
class PoliticaDeSyncTest {

    /** El parámetro es la cuenta ya incrementada: tras el 1er fallo se espera la primera pausa (15s). */
    @Test
    fun `la secuencia de backoff es 15s 30s 1m 5m 15m con techo`() {
        assertEquals(15.seconds, PoliticaDeSync.esperaDelIntento(1))
        assertEquals(30.seconds, PoliticaDeSync.esperaDelIntento(2))
        assertEquals(1.minutes, PoliticaDeSync.esperaDelIntento(3))
        assertEquals(5.minutes, PoliticaDeSync.esperaDelIntento(4))
        assertEquals(15.minutes, PoliticaDeSync.esperaDelIntento(5))
        // Techo: del 6º intento en adelante se queda en 15m, no sigue creciendo.
        assertEquals(15.minutes, PoliticaDeSync.esperaDelIntento(6))
        assertEquals(15.minutes, PoliticaDeSync.esperaDelIntento(7))
    }

    @Test
    fun `pasado el tope de 8 intentos ya no hay proximo intento automatico`() {
        assertNull(PoliticaDeSync.esperaDelIntento(PoliticaDeSync.MAXIMO_INTENTOS_AUTOMATICOS))
        assertNull(
            PoliticaDeSync.proximoIntento(
                PoliticaDeSync.MAXIMO_INTENTOS_AUTOMATICOS,
                RelojFijo(Instant.parse("2026-09-05T12:00:00Z")),
                TimeZone.UTC,
            ),
        )
    }

    @Test
    fun `proximoIntento suma la espera sobre el reloj del dispositivo`() {
        val reloj = RelojFijo(Instant.parse("2026-09-05T12:00:00Z"))

        assertEquals(LocalDateTime(2026, 9, 5, 12, 0, 15), PoliticaDeSync.proximoIntento(1, reloj, TimeZone.UTC))
        assertEquals(LocalDateTime(2026, 9, 5, 12, 5, 0), PoliticaDeSync.proximoIntento(4, reloj, TimeZone.UTC))
    }

    @Test
    fun `el tamano de fragmento elegido es 50 -- dentro del rango recomendado de 50 a 100`() {
        assertEquals(50, PoliticaDeSync.TAMANO_FRAGMENTO)
    }
}
