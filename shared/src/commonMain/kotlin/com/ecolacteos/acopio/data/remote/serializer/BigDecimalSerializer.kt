package com.ecolacteos.acopio.data.remote.serializer

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * El serializer más importante del proyecto (ver `CLAUDE.md §3.1`, `DATA-002`). Todo campo `BigDecimal` del
 * backend (litros, precios, porcentajes, GPS, medidas de laboratorio) pasa por acá.
 *
 * ⚠️ **Hallazgo de esta fase, documentado en el checkpoint**: `bignum` (`Decimal.parseString`) **no**
 * conserva los ceros finales del literal -- normaliza a dígitos significativos (`parseString("12.50").scale
 * == -1`, `toStringExpanded() == "12.5"`). Preservar el `12.50` exacto en la re-serialización exige conocer
 * la escala real de la columna Postgres del campo (`precision/scale` de `MOBILE_DATA_MAPPING.md §5`) y
 * reaplicarla al formatear -- exactamente lo que ya hace `Decimal.aTextoConEscala(escala)` de la Fase 1
 * (redondeo + relleno de ceros vía manipulación de texto, nunca vía `Double`). Por eso esta clase es
 * **abstracta y parametrizada por escala**, no un único `object` genérico: cada campo usa la instancia
 * concreta ([BigDecimalEscala2Serializer], [BigDecimalEscala3Serializer] o [BigDecimalEscala6Serializer])
 * que coincide con la escala real de su columna. `@Serializable(with = ...)` requiere un `object` (sin
 * argumentos de constructor), de ahí que sean 3 singletons en vez de una sola clase parametrizable.
 *
 * - Deserializa leyendo el literal crudo del JSON (`JsonPrimitive.content`) y construyendo el [Decimal]
 *   directamente desde ese `String` -- nunca via `decodeDouble()`.
 * - Serializa como número JSON sin comillas (vía `JsonUnquotedLiteral`), con la escala fija de esta
 *   instancia -- si el valor trae más decimales de los que la columna permite, se redondea half-up
 *   (mismo comportamiento que `aTextoConEscala`, documentado en la Fase 1).
 */
abstract class BigDecimalConEscalaSerializer(private val escala: Int) : KSerializer<Decimal> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.ecolacteos.acopio.Decimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Decimal) {
        val texto = value.aTextoConEscala(escala)
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(texto))
        } else {
            encoder.encodeString(texto)
        }
    }

    override fun deserialize(decoder: Decoder): Decimal {
        val texto = if (decoder is JsonDecoder) {
            // El literal crudo del JSON, vía JsonPrimitive.content -- nunca decodeDouble().
            val elemento = decoder.decodeJsonElement()
            (elemento as? JsonPrimitive)?.content
                ?: error("Se esperaba un JsonPrimitive para Decimal, se recibió: $elemento")
        } else {
            decoder.decodeString()
        }
        return Decimal.parseString(texto)
    }
}

/** Escala 2 -- la de la enorme mayoría de los campos monetarios/porcentuales del contrato (§5). */
object BigDecimalEscala2Serializer : BigDecimalConEscalaSerializer(escala = 2)

/** Escala 3 -- `PagoResponse.precioLitro` (`precision=6,scale=3`) y `AlertaAnomaliaResponse.zScore`. */
object BigDecimalEscala3Serializer : BigDecimalConEscalaSerializer(escala = 3)

/** Escala 6 -- `gpsLat`/`gpsLng` (`precision=9,scale=6`). */
object BigDecimalEscala6Serializer : BigDecimalConEscalaSerializer(escala = 6)
