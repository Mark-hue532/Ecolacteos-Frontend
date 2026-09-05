package com.ecolacteos.acopio.data.remote.serializer

import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * `java.time.LocalDate` del backend -- JSON `"2026-09-04"` (`yyyy-MM-dd`, `MOBILE_DATA_MAPPING.md §1.4`).
 * Sin hora, sin zona. No confundir con `ComunicadoResponse.fecha`, que pese al nombre es `LocalDateTime`
 * (ver [LocalDateTimeSerializer]).
 */
object LocalDateSerializer : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.ecolacteos.acopio.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}
