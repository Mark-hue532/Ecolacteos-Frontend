package com.ecolacteos.acopio.data.remote.dto

import kotlinx.serialization.Serializable

/** Nested DTO de `CambiosResponse.motivosObservacion` -- MATCH trivial (`MOBILE_DATA_MAPPING.md §5.6`). */
@Serializable
data class MotivoObservacionResponse(
    val id: String,
    val descripcion: String,
)
