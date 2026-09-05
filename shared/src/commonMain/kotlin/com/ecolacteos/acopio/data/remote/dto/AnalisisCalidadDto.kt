package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import com.ecolacteos.acopio.data.remote.serializer.LocalDateTimeSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * `POST /api/analisis-calidad` (OFFLINE-FIRST con dependencia, `MOBILE_DATA_MAPPING.md §5.3`).
 * `registroAcopioId` es el `id` de servidor del `RegistroAcopio` padre, **no** su `uuidCliente`
 * (AMBIGUOUS -- requiere la resolución de `server_id` de `MOBILE_ARCHITECTURE.md §18.1`, fuera de
 * alcance de esta fase). Los 6 parámetros de laboratorio son todos opcionales.
 */
@Serializable
data class AnalisisCalidadRequest(
    val uuidCliente: String,
    val registroAcopioId: String,
    val folioMuestra: String,
    @Serializable(with = BigDecimalEscala2Serializer::class) val agua: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val proteina: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val lactosa: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val densidad: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val temperatura: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val ph: Decimal? = null,
    val aguaAnadida: Boolean? = null,
)

@Serializable
data class AnalisisCalidadResponse(
    val id: String,
    val registroAcopioId: String,
    val folioMuestra: String,
    @Serializable(with = BigDecimalEscala2Serializer::class) val agua: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val proteina: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val lactosa: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val densidad: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val temperatura: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val ph: Decimal? = null,
    val aguaAnadida: Boolean,
    val resultado: ResultadoCalidad,
    @Serializable(with = LocalDateTimeSerializer::class) val creadoEn: LocalDateTime,
)
