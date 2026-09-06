package com.ecolacteos.acopio.synchronization

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Los dos números que `CLAUDE.md §7` y `PROMPT_FASE_05.md §2` dejan explícitamente a decisión del cliente,
 * fijados acá con valor concreto (no configurables sin default -- trampa #6).
 *
 * **Tamaño de fragmento = 50.** `MOBILE_DATA_MAPPING.md §5.6` confirma que el backend no declara ningún
 * límite (`@Size`) sobre el lote y recomienda al cliente trocear defensivamente entre 50 y 100. Se elige el
 * extremo bajo del rango por tres razones concretas de este dominio: los equipos son de gama baja
 * (`CLAUDE.md §1`), cada ítem viaja con varios decimales serializados como texto (no es un payload chico), y
 * un timeout a mitad de un fragmento obliga a reenviarlo entero -- con 50 se re-hace la mitad de trabajo
 * que con 100.
 *
 * **Backoff = 15s → 30s → 1m → 5m → 15m (techo), tope 8 intentos.** Es la secuencia que ejemplifica
 * `MOBILE_ARCHITECTURE.md §6.3`; se adopta tal cual en vez de inventar otra, porque el documento ya la
 * discutió contra el costo de batería. Pasado el intento 8, la fila deja de reintentarse sola y queda
 * `FAILED` permanente ("requiere revisión manual", §6.3) -- no se sigue drenando batería contra un
 * servidor caído.
 */
object PoliticaDeSync {

    const val TAMANO_FRAGMENTO: Int = 50

    const val MAXIMO_INTENTOS_AUTOMATICOS: Int = 8

    private val SECUENCIA_BACKOFF: List<Duration> =
        listOf(15.seconds, 30.seconds, 1.minutes, 5.minutes, 15.minutes)

    /**
     * Instante del próximo reintento, o `null` si ya se agotó el tope automático -- en cuyo caso el
     * llamador debe marcar la fila `FAILED` permanente.
     *
     * [intentosRealizados] es la cuenta **ya incrementada** por el fallo que se acaba de producir: tras el
     * primer fallo vale `1` y la espera es la primera de la secuencia (15s). Se calcula como "ahora del
     * dispositivo + espera"; el reloj y la zona entran por parámetro para que los tests sean deterministas.
     * La conversión `Instant → LocalDateTime` acá es legítima y **no** contradice `CLAUDE.md §3.2`:
     * `next_attempt_at` es bookkeeping local que genera este dispositivo, no un `LocalDateTime` ajeno del
     * backend que se estaría reinterpretando.
     */
    fun proximoIntento(
        intentosRealizados: Int,
        reloj: Clock = Clock.System,
        zona: TimeZone = TimeZone.currentSystemDefault(),
    ): LocalDateTime? {
        val espera = esperaDelIntento(intentosRealizados) ?: return null
        return (reloj.now() + espera).toLocalDateTime(zona)
    }

    /** La espera que sigue a [intentosRealizados] intentos fallidos, o `null` si se agotó el tope. */
    fun esperaDelIntento(intentosRealizados: Int): Duration? {
        if (intentosRealizados >= MAXIMO_INTENTOS_AUTOMATICOS) return null
        val indice = (intentosRealizados - 1).coerceIn(0, SECUENCIA_BACKOFF.lastIndex)
        return SECUENCIA_BACKOFF[indice]
    }
}
