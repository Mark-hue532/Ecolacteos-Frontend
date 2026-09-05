package com.ecolacteos.acopio.domain.model

import kotlinx.datetime.LocalDateTime

/** Modelo de dominio de `motivo_observacion_cache` (`MOBILE_ARCHITECTURE.md §11.2`). Dropdown de RegistroAcopio. */
data class MotivoObservacion(
    val id: String,
    val descripcion: String,
    val actualizadoEn: LocalDateTime,
)
