package com.ecolacteos.acopio.data.remote.serializer

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * `java.time.LocalDateTime` del backend -- JSON `"2026-09-04T10:15:30"` **o**
 * `"2026-09-04T10:15:30.123456"` si hay fracción de segundo, siempre **sin offset y sin `Z`**
 * (`MOBILE_DATA_MAPPING.md §1.4`). Al deserializar aceptamos también `"2026-09-04T10:15"` (sin segundos):
 * el formato ISO de `kotlinx.datetime.LocalDateTime.parse` ya cubre las tres formas -- segundos y fracción
 * son opcionales en el propio parser -- y no hay que restringirlo (se testea explícito, `DATA-001`).
 *
 * Al serializar **no** se usa `value.toString()`: ese `toString()` omite los segundos cuando son `0`
 * (`"2026-09-04T06:00"` en vez de `"...T06:00:00"`), heredado de `java.time.LocalDateTime` en la JVM. Se
 * arma el string a mano vía [horaConSegundosExplicitos] para que los segundos siempre estén.
 *
 * ⚠️ Este serializer **nunca** convierte a `Instant` ni asume una zona (CLAUDE.md §3.2, ver `DATA-001`
 * y `DATA-012`, todavía abiertos). Es hora de pared, se muestra tal cual llega.
 */
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.ecolacteos.acopio.LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        val hora = horaConSegundosExplicitos(value.hour, value.minute, value.second, value.nanosecond)
        encoder.encodeString("${value.date}T$hora")
    }

    override fun deserialize(decoder: Decoder): LocalDateTime =
        LocalDateTime.parse(decoder.decodeString())
}
