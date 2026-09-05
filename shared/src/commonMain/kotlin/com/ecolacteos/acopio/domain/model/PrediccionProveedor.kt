package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Modelo de dominio de `prediccion_proveedor_cache` (`MOBILE_ARCHITECTURE.md §11.2`). Solo lectura. */
data class PrediccionProveedor(
    val proveedorId: String,
    val fechaPrevista: LocalDate,
    val litrosEstimadosMin: Decimal,
    val litrosEstimadosMax: Decimal,
    val actualizadoEn: LocalDateTime,
)
