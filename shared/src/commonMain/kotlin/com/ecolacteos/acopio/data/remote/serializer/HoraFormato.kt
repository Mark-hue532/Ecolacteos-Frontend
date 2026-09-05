package com.ecolacteos.acopio.data.remote.serializer

/**
 * Arma `"HH:mm:ss"` (+ `.nnn`/`.nnnnnn`/`.nnnnnnnnn` si hay fracción) a mano, campo por campo.
 *
 * `LocalTime.toString()`/`LocalDateTime.toString()` de kotlinx-datetime (respaldados por
 * `java.time.LocalTime` en la JVM) **omiten los segundos cuando son `0`** -- emiten `"06:00"` en vez de
 * `"06:00:00"`. El backend, al deserializar con Jackson sobre `LocalTime`/`LocalDateTime`, acepta esa forma
 * corta sin problema, pero preferimos no depender de esa tolerancia al construir el body: se emite siempre
 * con segundos explícitos.
 *
 * El *deserializador* de cada tipo no usa esta función -- sigue siendo `LocalTime.parse`/
 * `LocalDateTime.parse`, que ya acepta con y sin segundos y con y sin fracción; no hay que restringirlo.
 */
internal fun horaConSegundosExplicitos(hour: Int, minute: Int, second: Int, nanosecond: Int): String =
    buildString {
        append(hour.toString().padStart(2, '0'))
        append(':')
        append(minute.toString().padStart(2, '0'))
        append(':')
        append(second.toString().padStart(2, '0'))
        if (nanosecond != 0) {
            append('.')
            when {
                nanosecond % 1_000_000 == 0 -> append((nanosecond / 1_000_000).toString().padStart(3, '0'))
                nanosecond % 1_000 == 0 -> append((nanosecond / 1_000).toString().padStart(6, '0'))
                else -> append(nanosecond.toString().padStart(9, '0'))
            }
        }
    }
