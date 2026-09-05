package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import com.ecolacteos.acopio.data.remote.serializer.LocalDateSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Nested DTO de `CambiosResponse.prediccionesProveedor`, idéntico al de
 * `GET /api/innovacion/prediccion/{id}` (`MOBILE_DATA_MAPPING.md §5.6`).
 */
@Serializable
data class PrediccionProveedorResponse(
    val proveedorId: String,
    @Serializable(with = LocalDateSerializer::class) val fechaPrevista: LocalDate,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosEstimadosMin: Decimal,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosEstimadosMax: Decimal,
)
