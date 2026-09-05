package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.data.remote.serializer.LocalTimeSerializer
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * `GET /api/zonas/{zonaId}/ruta` (`MOBILE_DATA_MAPPING.md §5.8`). `horaEstimada` es el único campo
 * `LocalTime` de todo el contrato, y es nullable.
 */
@Serializable
data class RutaProveedorOrdenResponse(
    val id: String,
    val proveedorId: String,
    val proveedorNombre: String,
    val orden: Int,
    @Serializable(with = LocalTimeSerializer::class) val horaEstimada: LocalTime? = null,
)
