package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.CicloCapital
import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio de `tipo_queso_cache` (`MOBILE_ARCHITECTURE.md §11.2`). [CicloCapital] se reusa de
 * `data/remote/dto/` (mismo criterio que [Venta] con `TipoClienteVenta` -- fallback `UNKNOWN` incluido).
 */
data class TipoQueso(
    val id: String,
    val nombre: String,
    val rendimientoEsperadoPct: Decimal,
    val cicloCapital: CicloCapital,
    val activo: Boolean,
    val actualizadoEn: LocalDateTime,
)
