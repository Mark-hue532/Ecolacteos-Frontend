package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.data.remote.serializer.LocalDateTimeSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * `POST /api/comunicados/{id}/confirmaciones` (`MOBILE_DATA_MAPPING.md §5.7`). Sin `uuidCliente`
 * (`DATA-005`, no idempotente hoy). El `acopiadorId`/`acopiadorNombre` de la Response se resuelven del
 * JWT, nunca del body.
 */
@Serializable
data class ConfirmarComunicadoRequest(
    val proveedorId: String,
)

@Serializable
data class ComunicadoConfirmacionResponse(
    val id: String,
    val proveedorId: String,
    val proveedorNombre: String,
    val acopiadorId: String,
    val acopiadorNombre: String,
    @Serializable(with = LocalDateTimeSerializer::class) val confirmadoEn: LocalDateTime,
)
