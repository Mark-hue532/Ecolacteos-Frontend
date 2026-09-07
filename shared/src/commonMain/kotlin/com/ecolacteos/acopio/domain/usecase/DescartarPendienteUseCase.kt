package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.data.repository.LoteProduccionRepository
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.data.repository.VentaRepository
import com.ecolacteos.acopio.synchronization.RecursoSync

/**
 * `S-05` (`MOBILE_SCREENS.md §4`, regla 3): la única forma de que trabajo no confirmado salga de la base
 * por decisión del usuario (`CLAUDE.md §3.6`). Cada Repository filtra por `usuario_id` de la sesión activa
 * y por `sync_status <> SYNCED` -- ver el `.sq` de cada recurso.
 */
class DescartarPendienteUseCase(
    private val registroAcopioRepository: RegistroAcopioRepository,
    private val analisisCalidadRepository: AnalisisCalidadRepository,
    private val loteProduccionRepository: LoteProduccionRepository,
    private val ventaRepository: VentaRepository,
) {
    suspend operator fun invoke(recurso: RecursoSync, uuidCliente: String) {
        when (recurso) {
            RecursoSync.REGISTRO_ACOPIO -> registroAcopioRepository.descartar(uuidCliente)
            RecursoSync.ANALISIS_CALIDAD -> analisisCalidadRepository.descartar(uuidCliente)
            RecursoSync.LOTE_PRODUCCION -> loteProduccionRepository.descartar(uuidCliente)
            RecursoSync.VENTA -> ventaRepository.descartar(uuidCliente)
        }
    }
}
