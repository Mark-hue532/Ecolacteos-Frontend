package com.ecolacteos.acopio.domain.model

import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio de `comunicado_cache` + `comunicado_zona_cache` (`MOBILE_ARCHITECTURE.md §11.2`).
 * [zonasNombres] compone en un solo objeto las filas de la tabla hija -- igual que
 * `ComunicadoResponse.zonasNombres` ya las compone en un solo campo del lado del DTO (Fase 2). ⚠️ [fecha]
 * es `LocalDateTime`, no `LocalDate`, pese al nombre (`MOBILE_DATA_MAPPING.md §5.6`, la trampa más fácil de
 * pasar por alto de esta fase).
 */
data class Comunicado(
    val id: String,
    val mensaje: String,
    val fecha: LocalDateTime,
    val zonasNombres: List<String>,
    val actualizadoEn: LocalDateTime,
)
