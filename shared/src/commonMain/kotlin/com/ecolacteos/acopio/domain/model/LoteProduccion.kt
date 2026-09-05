package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio de `lote_produccion_local` (`MOBILE_ARCHITECTURE.md §11.1`). Los registros de acopio
 * que consume este lote (N:M) **no** viven en esta data class -- son [LoteProduccionRegistro], filas
 * independientes de `lote_produccion_registro_local` referenciadas por [uuidCliente], igual que el
 * esquema las modela como tabla aparte.
 */
data class LoteProduccion(
    val uuidCliente: String,
    val serverId: String?,
    val usuarioId: String,
    val fecha: LocalDate,
    val tipoQuesoId: String,
    val litrosUsados: Decimal,
    val unidadesObtenidas: Int,
    val syncStatus: SyncStatus,
    val syncAttempts: Int,
    val syncError: String?,
    val nextAttemptAt: LocalDateTime?,
    val creadoEn: LocalDateTime,
    val sincronizadoEn: LocalDateTime?,
)

/**
 * Fila de `lote_produccion_registro_local` -- espejo local del N:M `lote_registro_acopio`
 * (`MOBILE_ARCHITECTURE.md §11.1`). No es uno de los modelos que enumera `PROMPT_FASE_04.md §2`
 * explícitamente, pero la tabla existe y necesita un tipo para su mapper (decisión de esta fase, ver
 * checkpoint). Sin `syncStatus` propio a propósito (trampa #3 del prompt): esta fila sincroniza junto con
 * su [LoteProduccion] padre, nunca sola.
 *
 * Mismo patrón de doble referencia que [AnalisisCalidad] (C-02): exactamente una de
 * [registroAcopioUuidCliente] / [registroAcopioServerId] es no-nula.
 */
data class LoteProduccionRegistro(
    val loteUuidCliente: String,
    val registroAcopioUuidCliente: String?,
    val registroAcopioServerId: String?,
) {
    init {
        require((registroAcopioUuidCliente == null) != (registroAcopioServerId == null)) {
            "Exactamente una de registroAcopioUuidCliente/registroAcopioServerId debe ser no-nula (C-02)"
        }
    }
}
