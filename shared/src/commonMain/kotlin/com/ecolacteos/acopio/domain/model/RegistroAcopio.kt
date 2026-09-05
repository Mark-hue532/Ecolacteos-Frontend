package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio de `registro_acopio_local` (`MOBILE_ARCHITECTURE.md §11.1`). Captura propia del
 * dispositivo, con ciclo de vida de sync completo -- no confundir con [RegistroAcopioReferencia], que es
 * un registro ajeno de solo lectura (`registro_acopio_cache`, C-04, ver `DATA-013`).
 *
 * Mismos tipos que ya fijó Fase 2 para el DTO equivalente donde hay overlap (`RegistroAcopioDTO`):
 * [Decimal] para litros/GPS (nunca `Double`, `CLAUDE.md §3.1`), `LocalDateTime` de pared para `fechaHora`
 * (nunca convertido a `Instant`, `CLAUDE.md §3.2`).
 */
data class RegistroAcopio(
    val uuidCliente: String,
    val serverId: String?,
    val usuarioId: String,
    val proveedorId: String,
    val unidadId: String,
    val fechaHora: LocalDateTime,
    val litros: Decimal,
    val gpsLat: Decimal?,
    val gpsLng: Decimal?,
    val motivoObservacionId: String?,
    val litrosPorVoz: Boolean,
    val syncStatus: SyncStatus,
    val syncAttempts: Int,
    val syncError: String?,
    val nextAttemptAt: LocalDateTime?,
    val creadoEn: LocalDateTime,
    val sincronizadoEn: LocalDateTime?,
)
