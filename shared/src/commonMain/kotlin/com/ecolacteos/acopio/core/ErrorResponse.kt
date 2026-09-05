package com.ecolacteos.acopio.core

import kotlinx.serialization.Serializable

/**
 * Forma única de error de toda la API (`MOBILE_DATA_MAPPING.md §5.12`): todo endpoint MOBILE, con
 * cualquier código de error (400/401/403/404/409/422/500), responde exactamente esta forma.
 *
 * Solo el modelo en esta fase -- el interceptor que lo decodifica a [ApiError] es de la Fase 2.
 */
@Serializable
data class ErrorResponse(
    val timestamp: String,
    val status: Int,
    val error: String,
    val mensaje: String,
)
