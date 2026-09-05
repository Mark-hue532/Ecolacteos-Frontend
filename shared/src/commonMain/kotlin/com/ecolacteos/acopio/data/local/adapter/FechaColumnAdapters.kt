package com.ecolacteos.acopio.data.local.adapter

import app.cash.sqldelight.ColumnAdapter
import com.ecolacteos.acopio.data.remote.serializer.horaConSegundosExplicitos
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * `LocalDateTime ↔ TEXT`, sin conversión de zona en ningún sentido (`DATA-001`, `CLAUDE.md §3.2`) --
 * misma semántica de hora de pared que `LocalDateTimeSerializer` de Fase 2.
 *
 * Al codificar **no** se usa `value.toString()` (trampa #5 de `PROMPT_FASE_04.md`: ese `toString()` omite
 * los segundos cuando son `0`, heredado de `java.time.LocalDateTime` en la JVM -- el mismo bug que
 * `HoraFormato.kt` ya corrigió del lado de la serialización JSON). Se reusa
 * [horaConSegundosExplicitos] (mismo helper `internal`, visible en este módulo) en vez de escribir un
 * segundo formateador que puede reintroducirlo.
 */
object LocalDateTimeColumnAdapter : ColumnAdapter<LocalDateTime, String> {
    override fun decode(databaseValue: String): LocalDateTime = LocalDateTime.parse(databaseValue)
    override fun encode(value: LocalDateTime): String {
        val hora = horaConSegundosExplicitos(value.hour, value.minute, value.second, value.nanosecond)
        return "${value.date}T$hora"
    }
}

/** `LocalDate ↔ TEXT` (`yyyy-MM-dd`) -- sin hora, sin zona, sin el bug de segundos (no aplica: no hay hora). */
object LocalDateColumnAdapter : ColumnAdapter<LocalDate, String> {
    override fun decode(databaseValue: String): LocalDate = LocalDate.parse(databaseValue)
    override fun encode(value: LocalDate): String = value.toString()
}

/**
 * `LocalTime ↔ TEXT` -- único uso en todo el esquema: `ruta_zona_cache.hora_estimada`. Mismo fix de
 * segundos explícitos que [LocalDateTimeColumnAdapter], vía el mismo helper reusado.
 */
object LocalTimeColumnAdapter : ColumnAdapter<LocalTime, String> {
    override fun decode(databaseValue: String): LocalTime = LocalTime.parse(databaseValue)
    override fun encode(value: LocalTime): String =
        horaConSegundosExplicitos(value.hour, value.minute, value.second, value.nanosecond)
}
