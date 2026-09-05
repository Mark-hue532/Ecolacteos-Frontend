package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala3Serializer
import com.ecolacteos.acopio.data.remote.serializer.LocalDateSerializer
import com.ecolacteos.acopio.data.remote.serializer.LocalDateTimeSerializer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/** Lectura móvil, CALIDAD (`MOBILE_DATA_MAPPING.md §5.11`). */
@Serializable
data class ScoreConfianzaResponse(
    val proveedorId: String,
    @Serializable(with = LocalDateSerializer::class) val periodo: LocalDate,
    @Serializable(with = BigDecimalEscala2Serializer::class) val score: Decimal,
    @Serializable(with = BigDecimalEscala2Serializer::class) val componenteCalidad: Decimal,
    @Serializable(with = BigDecimalEscala2Serializer::class) val componenteRegularidad: Decimal,
    @Serializable(with = BigDecimalEscala2Serializer::class) val componenteAnomalias: Decimal,
)

/**
 * `GET /api/innovacion/alertas?zonaId={UUID}` -- `zonaId` es **obligatorio**, `400` si se omite
 * (`MOBILE_DATA_MAPPING.md §5.11`, §7). `zScore` es nullable por schema, escala 3
 * (`schema.sql`: `z_score NUMERIC(6,3)`).
 */
@Serializable
data class AlertaAnomaliaResponse(
    val id: String,
    val registroAcopioId: String,
    val proveedorId: String,
    val proveedorNombre: String,
    val tipo: TipoAlerta,
    @Serializable(with = BigDecimalEscala3Serializer::class) val zScore: Decimal? = null,
    val severidad: Severidad,
    @Serializable(with = LocalDateTimeSerializer::class) val creadoEn: LocalDateTime,
)
