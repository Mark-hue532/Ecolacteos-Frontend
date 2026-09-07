package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDate

/**
 * Detalle de un `LoteProduccion` para `P-04` (`MOBILE_SCREENS.md §7`, ONLINE+CACHE). Distinto de
 * [LoteProduccion] (Fase 6) porque puede construirse desde dos fuentes de fidelidad decreciente, mismo
 * patrón que `RegistroAcopioDetalle` (`A-06`) y `AnalisisCalidadDetalle` (`C-04`):
 *
 * 1. `GET /api/lotes-produccion/{id}` exitoso -- trae [tipoQuesoNombre] (texto) y [rendimientoPct] real,
 *    pero **no** el `id` del tipo de queso (mismo `NAME_MISMATCH` de `RegistroAcopioResponse.motivoObservacion`,
 *    `MOBILE_DATA_MAPPING.md §5.2`) ni `creadoEn`.
 * 2. `lote_produccion_local` (la propia captura de este dispositivo) -- sin conexión o con error,
 *    degradado: trae [tipoQuesoId] pero no [tipoQuesoNombre], y [rendimientoPct] queda `null` (el
 *    servidor todavía no lo calculó, y calcularlo local sería inventarlo -- trampa #6).
 *
 * [tipoQuesoId]/[tipoQuesoNombre] conviven a propósito, igual que `motivoObservacionId`/`motivoObservacionTexto`
 * en `RegistroAcopioDetalle` -- nunca se confunden entre sí. El `ViewModel` resuelve el nombre (y, si
 * degradado, también `rendimientoEsperadoPct`) contra `tipo_queso_cache` cuando hace falta, mismo patrón
 * que `DetalleRegistroAcopioViewModel` con `motivoObservacionTexto`.
 */
data class LoteProduccionDetalle(
    val id: String,
    val fecha: LocalDate,
    val tipoQuesoId: String?,
    val tipoQuesoNombre: String?,
    val litrosUsados: Decimal,
    val unidadesObtenidas: Int,
    val rendimientoPct: Decimal?,
    val rendimientoEsperadoPct: Decimal?,
)
