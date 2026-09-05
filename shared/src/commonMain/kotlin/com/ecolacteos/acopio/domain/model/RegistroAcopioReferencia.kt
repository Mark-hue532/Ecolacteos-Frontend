package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio de `registro_acopio_cache` (`MOBILE_ARCHITECTURE.md §11.2`, C-04). Registro de acopio
 * **ajeno** (capturado por otro dispositivo), solo lectura, nunca se crea ni edita localmente -- nombrado
 * distinto de [RegistroAcopio] a propósito: confundirlos sería el mismo error de diseño que `DATA-013` ya
 * señaló a nivel de contrato (`PROMPT_FASE_04.md §2`).
 *
 * [uuidCliente]/[proveedorId]/[proveedorNombre] son `NULL`ables porque el DTO resumen
 * (`RegistroAcopioResumenResponse`) no los trae -- ver [Origen]. [tieneObservacion] solo viene en ese
 * mismo DTO resumen (nulo si esta fila se pobló desde el detalle).
 */
data class RegistroAcopioReferencia(
    val id: String,
    val uuidCliente: String?,
    val proveedorId: String?,
    val proveedorNombre: String?,
    val fechaHora: LocalDateTime,
    val litros: Decimal,
    val tieneObservacion: Boolean?,
    val origen: Origen,
    val actualizadoEn: LocalDateTime,
)
