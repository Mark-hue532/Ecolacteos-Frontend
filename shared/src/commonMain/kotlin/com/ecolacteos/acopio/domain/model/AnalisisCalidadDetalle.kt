package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.ResultadoCalidad
import kotlinx.datetime.LocalDateTime

/**
 * Detalle de un `AnalisisCalidad` para `C-04` (`MOBILE_SCREENS.md §6`, ONLINE+CACHE). Distinto de
 * [AnalisisCalidad] (Fase 6) porque agrega [resultado] -- lo calcula el servidor, nunca viaja en
 * `analisis_calidad_local` (ver el comentario de [AnalisisCalidad]) -- y puede construirse desde dos
 * fuentes de fidelidad decreciente, igual que `RegistroAcopioDetalle` (`A-06`):
 *
 * 1. `GET /api/analisis-calidad/registro/{registroAcopioId}` exitoso -- todos los campos, [resultado]
 *    confirmado por el servidor.
 * 2. `analisis_calidad_local` (la propia captura de este dispositivo, ya con un padre resuelto) -- sin
 *    conexión o con error, degradado. [resultado] queda `null`: mientras la fila no sea `SYNCED` el
 *    servidor todavía no lo calculó, y mostrar un valor "previsto" sería el mismo error que `C-03` evita
 *    con el propio campo (`§6`, trampa #9).
 *
 * [resultado] reutiliza el `enum` del DTO (`data/remote/dto/ResultadoCalidad`) en vez de duplicarlo a nivel
 * de dominio -- mismo criterio ya establecido por `VentaDetalle.tipoCliente` (Fase 7) para un enum abierto
 * que viaja igual en los dos sentidos.
 */
data class AnalisisCalidadDetalle(
    val registroAcopioId: String,
    val folioMuestra: String,
    val agua: Decimal?,
    val proteina: Decimal?,
    val lactosa: Decimal?,
    val densidad: Decimal?,
    val temperatura: Decimal?,
    val ph: Decimal?,
    val aguaAnadida: Boolean,
    val resultado: ResultadoCalidad?,
    val creadoEn: LocalDateTime,
)
