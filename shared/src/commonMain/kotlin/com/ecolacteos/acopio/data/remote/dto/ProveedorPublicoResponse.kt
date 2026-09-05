package com.ecolacteos.acopio.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Nested DTO de `CambiosResponse.proveedores` (`MOBILE_DATA_MAPPING.md §5.6`). Es también el mismo DTO
 * detrás de `GET /api/proveedores/qr/{codigoQr}` (`MOBILE_ARCHITECTURE.md §3.3`) -- el móvil solo usa esta
 * versión "pública" (RNF-12: sin DNI/teléfono), nunca `ProveedorAdminResponse`.
 */
@Serializable
data class ProveedorPublicoResponse(
    val id: String,
    val nombre: String,
    val zonaActualId: String? = null,
    val zonaActualNombre: String? = null,
    val codigoQr: String? = null,
)
