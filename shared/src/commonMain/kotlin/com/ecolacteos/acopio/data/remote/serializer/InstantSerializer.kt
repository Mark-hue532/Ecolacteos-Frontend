package com.ecolacteos.acopio.data.remote.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * `java.time.Instant` del backend -- JSON `"2026-09-04T15:30:00.123456Z"`, **con** `Z` (UTC explícito,
 * `MOBILE_DATA_MAPPING.md §1.4`). Aparece en **un solo campo de todo el contrato**: `CambiosResponse.
 * generadoEn`. Si algo más usa este serializer, es señal de que se está modelando mal un `LocalDateTime`
 * (ver [LocalDateTimeSerializer] y `DATA-001`).
 */
object InstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.ecolacteos.acopio.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}
