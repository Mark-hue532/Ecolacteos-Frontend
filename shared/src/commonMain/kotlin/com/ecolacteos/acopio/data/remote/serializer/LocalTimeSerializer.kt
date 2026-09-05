package com.ecolacteos.acopio.data.remote.serializer

import kotlinx.datetime.LocalTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * `java.time.LocalTime` del backend -- JSON `"14:30:00"` (`MOBILE_DATA_MAPPING.md §1.4`). Aparece en un
 * solo campo de todo el contrato: `RutaProveedorOrdenResponse.horaEstimada` (nullable).
 */
object LocalTimeSerializer : KSerializer<LocalTime> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.ecolacteos.acopio.LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalTime =
        LocalTime.parse(decoder.decodeString())
}
