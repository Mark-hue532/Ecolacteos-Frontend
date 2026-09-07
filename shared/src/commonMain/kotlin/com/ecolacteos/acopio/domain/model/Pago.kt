package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDate

/**
 * `Pago` (`PROMPT_FASE_08E.md §4`, ONLINE-ONLY, solo lectura -- el móvil no genera pagos). Sin tabla
 * local (`MOBILE_ARCHITECTURE.md §11.3`). [precioLitro] tiene **3 decimales**
 * (`precision=6, scale=3`) -- distinto de todos los demás campos monetarios del contrato, que usan 2
 * (`§10.1`, trampa #5). [total] es columna `GENERATED`, de solo lectura.
 */
data class Pago(
    val id: String,
    val proveedorId: String,
    val proveedorNombre: String,
    val semanaInicio: LocalDate,
    val semanaFin: LocalDate,
    val litrosTotales: Decimal,
    val precioLitro: Decimal,
    val total: Decimal,
    val comprobanteGenerado: Boolean,
)
