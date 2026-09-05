package com.ecolacteos.acopio.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Modelo de dominio de `ruta_zona_cache` (`MOBILE_ARCHITECTURE.md §11.2`). Se descarga bajo demanda por
 * `GET /zonas/{zonaId}/ruta` -- no viaja en `/sync/cambios`, su "reemplazo" está acotado a [zonaId], nunca
 * es un `DELETE` de la tabla entera (§18.5).
 */
data class RutaProveedorOrden(
    val zonaId: String,
    val proveedorId: String,
    val proveedorNombre: String,
    val orden: Int,
    val horaEstimada: LocalTime?,
    val actualizadoEn: LocalDateTime,
)
