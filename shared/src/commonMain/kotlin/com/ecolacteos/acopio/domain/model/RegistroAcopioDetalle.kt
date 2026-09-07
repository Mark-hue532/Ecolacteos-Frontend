package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDateTime

/**
 * Detalle de un `RegistroAcopio` para `A-06` (`MOBILE_SCREENS.md §5`, ONLINE+CACHE). Distinto de
 * [RegistroAcopio] (Fase 6) porque puede construirse desde tres fuentes de fidelidad decreciente -- ver
 * `RegistroAcopioRepository.obtenerDetalle`:
 *
 * 1. `GET /api/registros-acopio/{id}` exitoso -- todos los campos presentes.
 * 2. `registro_acopio_cache` (registro ajeno ya visto en `A-05`) -- sin `unidadId`, sin GPS, sin
 *    `motivoObservacionTexto`, sin `sincronizadoEn`.
 * 3. `registro_acopio_local` (registro **propio**, capturado en este dispositivo) -- sin
 *    `motivoObservacionTexto` (solo el id: la descripción es un campo del Response, `NAME_MISMATCH` de
 *    `MOBILE_DATA_MAPPING.md §5.2`) ni `proveedorNombre`; el `ViewModel` resuelve ambos contra el catálogo
 *    local si hace falta, mismo patrón que `DetalleVentaViewModel` con `tipoQuesoNombre`.
 *
 * [motivoObservacionId]/[motivoObservacionTexto] conviven a propósito -- nunca se confunden entre sí
 * (trampa #9 de `PROMPT_FASE_08A.md`).
 */
data class RegistroAcopioDetalle(
    val id: String,
    val uuidCliente: String?,
    val proveedorId: String?,
    val proveedorNombre: String?,
    val unidadId: String?,
    val fechaHora: LocalDateTime,
    val litros: Decimal,
    val gpsLat: Decimal?,
    val gpsLng: Decimal?,
    val motivoObservacionId: String?,
    val motivoObservacionTexto: String?,
    val litrosPorVoz: Boolean?,
    val sincronizadoEn: LocalDateTime?,
)
