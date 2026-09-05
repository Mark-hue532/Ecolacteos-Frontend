package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import kotlinx.serialization.Serializable

/**
 * Nested DTO de `CambiosResponse.unidades` -- **sin** filtrar por ningún estado, `unidad` no tiene columna
 * `activo` (`MOBILE_DATA_MAPPING.md §5.6`).
 */
@Serializable
data class UnidadResponse(
    val id: String,
    val placa: String,
    @Serializable(with = BigDecimalEscala2Serializer::class) val capacidadTon: Decimal? = null,
    val zonaId: String? = null,
    val responsableId: String,
    val responsableNombre: String,
)
