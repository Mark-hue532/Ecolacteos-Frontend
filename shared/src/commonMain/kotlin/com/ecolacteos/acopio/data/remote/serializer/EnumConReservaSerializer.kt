package com.ecolacteos.acopio.data.remote.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Base reusable para los enums del contrato (`MOBILE_DATA_MAPPING.md §1.6`): todos viajan como `String`
 * libre, sin garantía de conjunto cerrado por parte del backend (los Response DTO los aplanan con
 * `.name()`, no hay un `enum` real a nivel de OpenAPI). Un valor no reconocido decodifica al [reserva]
 * (`UNKNOWN`), **nunca** lanza excepción -- un valor de dominio nuevo (ej. `OBSERVADO`, documentado pero sin
 * productor hoy) no puede tumbar la deserialización de toda la respuesta.
 *
 * Cada enum del contrato define su propio `object` de serializer heredando de esta clase en vez de repetir
 * la lógica de "buscar por nombre, si no está devolver la reserva".
 */
abstract class EnumConReservaSerializer<T : Enum<T>>(
    serialName: String,
    private val valores: Array<T>,
    private val reserva: T,
) : KSerializer<T> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): T {
        val texto = decoder.decodeString()
        return valores.firstOrNull { it.name == texto } ?: reserva
    }
}
