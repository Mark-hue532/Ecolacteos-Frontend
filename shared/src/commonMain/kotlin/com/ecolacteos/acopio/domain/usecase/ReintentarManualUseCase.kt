package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.data.repository.LoteProduccionRepository
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.data.repository.VentaRepository
import com.ecolacteos.acopio.synchronization.RecursoSync

/**
 * Fuerza un intento fuera del ciclo automático para un ítem `FAILED` (`§5`) -- permanente o transitorio,
 * el usuario puede pedirlo igual. Reinicia `sync_attempts` a 0 (presupuesto nuevo de backoff) y limpia
 * `sync_error`/`next_attempt_at`, después dispara `solicitarSyncOportunista()`.
 */
class ReintentarManualUseCase(
    private val registroAcopioRepository: RegistroAcopioRepository,
    private val analisisCalidadRepository: AnalisisCalidadRepository,
    private val loteProduccionRepository: LoteProduccionRepository,
    private val ventaRepository: VentaRepository,
) {
    operator fun invoke(recurso: RecursoSync, uuidCliente: String) {
        when (recurso) {
            RecursoSync.REGISTRO_ACOPIO -> registroAcopioRepository.reintentar(uuidCliente)
            RecursoSync.ANALISIS_CALIDAD -> analisisCalidadRepository.reintentar(uuidCliente)
            RecursoSync.LOTE_PRODUCCION -> loteProduccionRepository.reintentar(uuidCliente)
            RecursoSync.VENTA -> ventaRepository.reintentar(uuidCliente)
        }
    }
}
