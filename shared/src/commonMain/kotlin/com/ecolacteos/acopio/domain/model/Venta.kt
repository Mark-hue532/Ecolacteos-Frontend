package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio de `venta_local` (`MOBILE_ARCHITECTURE.md §11.1`). Sin dependencias externas
 * (a diferencia de `AnalisisCalidad`/`LoteProduccion`), nunca usa `PENDING_DEPENDENCY`.
 *
 * [TipoClienteVenta] se reusa tal cual de `data/remote/dto/` (`PROMPT_FASE_04.md §2`: "los enums que ya
 * definió Fase 2 -- reusalos, no los redefinas en domain/"), pese a que `domain/` normalmente no debería
 * ver DTOs de red -- es el enum de dominio real del backend, no un detalle de transporte, y ya trae el
 * fallback `UNKNOWN` (`DATA-010`) que un enum nuevo solo duplicaría.
 */
data class Venta(
    val uuidCliente: String,
    val serverId: String?,
    val usuarioId: String,
    val fecha: LocalDate,
    val tipoCliente: TipoClienteVenta,
    val tipoQuesoId: String,
    val cantidad: Int,
    val precioUnitario: Decimal,
    val syncStatus: SyncStatus,
    val syncAttempts: Int,
    val syncError: String?,
    val nextAttemptAt: LocalDateTime?,
    val creadoEn: LocalDateTime,
    val sincronizadoEn: LocalDateTime?,
)
