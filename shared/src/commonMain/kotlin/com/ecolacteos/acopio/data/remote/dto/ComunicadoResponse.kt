package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.data.remote.serializer.LocalDateTimeSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * Nested DTO de `CambiosResponse.comunicados`. ⚠️ `fecha` es **`LocalDateTime`**, no `LocalDate`, pese al
 * nombre engañoso (`MOBILE_DATA_MAPPING.md §5.6`) -- es la trampa explícita más fácil de pasar por alto de
 * esta fase.
 */
@Serializable
data class ComunicadoResponse(
    val id: String,
    val mensaje: String,
    @Serializable(with = LocalDateTimeSerializer::class) val fecha: LocalDateTime,
    val zonasNombres: List<String>,
)
