package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.data.repository.LoteProduccionRepository
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.data.repository.VentaRepository
import com.ecolacteos.acopio.domain.model.AnalisisCalidad
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.model.LoteProduccion
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.Venta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Un ítem de cualquiera de los 4 recursos, con su [EstadoSincronizacion] ya resuelto (`§7`, no un `SyncStatus` crudo). */
data class ConEstado<T>(val dato: T, val estado: EstadoSincronizacion)

/**
 * Los 4 `Flow` de pendientes unidos (`§5`: "expone el estado tal cual sección 7 exige, no lo resume") --
 * para la futura pantalla `S-05`. Cada lista excluye `SYNCED` (ya no es "pendiente" de nada); el filtro se
 * hace **después** de mapear a [EstadoSincronizacion], nunca antes, para no perder la distinción de §7.
 */
data class ResumenPendientes(
    val registros: List<ConEstado<RegistroAcopio>>,
    val analisis: List<ConEstado<AnalisisCalidad>>,
    val lotes: List<ConEstado<LoteProduccion>>,
    val ventas: List<ConEstado<Venta>>,
) {
    val total: Int get() = registros.size + analisis.size + lotes.size + ventas.size
}

class ObservarPendientesUseCase(
    private val registroAcopioRepository: RegistroAcopioRepository,
    private val analisisCalidadRepository: AnalisisCalidadRepository,
    private val loteProduccionRepository: LoteProduccionRepository,
    private val ventaRepository: VentaRepository,
) {
    operator fun invoke(): Flow<ResumenPendientes> = combine(
        registroAcopioRepository.observarPendientes(),
        analisisCalidadRepository.observarPendientes(),
        loteProduccionRepository.observarPendientes(),
        ventaRepository.observarPendientes(),
    ) { registros, analisis, lotes, ventas ->
        ResumenPendientes(
            registros = registros.filter { it.syncStatus != SyncStatus.SYNCED }
                .map { ConEstado(it, estadoSincronizacionDe(it.syncStatus, it.syncError, it.nextAttemptAt)) },
            analisis = analisis.filter { it.syncStatus != SyncStatus.SYNCED }
                .map { ConEstado(it, estadoSincronizacionDe(it.syncStatus, it.syncError, it.nextAttemptAt)) },
            lotes = lotes.filter { it.syncStatus != SyncStatus.SYNCED }
                .map { ConEstado(it, estadoSincronizacionDe(it.syncStatus, it.syncError, it.nextAttemptAt)) },
            ventas = ventas.filter { it.syncStatus != SyncStatus.SYNCED }
                .map { ConEstado(it, estadoSincronizacionDe(it.syncStatus, it.syncError, it.nextAttemptAt)) },
        )
    }
}
