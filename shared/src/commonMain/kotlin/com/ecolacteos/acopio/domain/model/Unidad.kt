package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDateTime

/** Modelo de dominio de `unidad_cache` (`MOBILE_ARCHITECTURE.md §11.2`). Dropdown de RegistroAcopio. */
data class Unidad(
    val id: String,
    val placa: String,
    val capacidadTon: Decimal?,
    val zonaId: String?,
    val responsableId: String,
    val responsableNombre: String,
    val actualizadoEn: LocalDateTime,
)
