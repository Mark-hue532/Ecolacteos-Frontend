package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio de `analisis_calidad_local` (`MOBILE_ARCHITECTURE.md §11.1`). **Exactamente una** de
 * [registroAcopioUuidCliente] / [registroAcopioServerId] es no-nula (C-02, el mismo `CHECK` del esquema):
 * padre capturado en este dispositivo (su `server_id` se resuelve antes de enviar, `§18.1`) o padre ajeno
 * (ya resuelto vía `registro_acopio_cache`, caso normal para CALIDAD).
 *
 * No incluye `resultado` (`ResultadoCalidad`): ese campo lo calcula el servidor y solo viaja en
 * `AnalisisCalidadResponse` -- §11.1 no lo declara como columna local, no se inventa acá.
 */
data class AnalisisCalidad(
    val uuidCliente: String,
    val serverId: String?,
    val usuarioId: String,
    val registroAcopioUuidCliente: String?,
    val registroAcopioServerId: String?,
    val folioMuestra: String,
    val agua: Decimal?,
    val proteina: Decimal?,
    val lactosa: Decimal?,
    val densidad: Decimal?,
    val temperatura: Decimal?,
    val ph: Decimal?,
    val aguaAnadida: Boolean,
    val syncStatus: SyncStatus,
    val syncAttempts: Int,
    val syncError: String?,
    val nextAttemptAt: LocalDateTime?,
    val creadoEn: LocalDateTime,
    val sincronizadoEn: LocalDateTime?,
) {
    init {
        require((registroAcopioUuidCliente == null) != (registroAcopioServerId == null)) {
            "Exactamente una de registroAcopioUuidCliente/registroAcopioServerId debe ser no-nula (C-02)"
        }
    }
}
