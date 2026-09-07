package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Una fila de `C-01` -- la entrega ajena cacheada, con si ya tiene un análisis de la sesión activa o no. */
data class EntregaConEstadoAnalisis(val referencia: RegistroAcopioReferencia, val tieneAnalisis: Boolean)

/**
 * `C-01 · Home calidad` (Fase 8C, `MOBILE_SCREENS.md §6`) -- fuente literal del documento:
 * `registro_acopio_cache` + `analisis_calidad_local`, combinados reactivamente. `tieneAnalisis` se resuelve
 * por `registroAcopioServerId`: es la única referencia que un análisis sobre una entrega ajena puede tener
 * (`§18.1` mecanismo 2) -- nunca `registroAcopioUuidCliente`, que apunta a un padre **propio**.
 */
class ObservarEntregasConEstadoAnalisisUseCase(
    private val registroAcopioRepository: RegistroAcopioRepository,
    private val analisisCalidadRepository: AnalisisCalidadRepository,
) {
    operator fun invoke(): Flow<List<EntregaConEstadoAnalisis>> = combine(
        registroAcopioRepository.observarEntregasCacheadas(),
        analisisCalidadRepository.observarPendientes(),
    ) { entregas, analisis ->
        val idsConAnalisis = analisis.mapNotNull { it.registroAcopioServerId }.toSet()
        entregas.map { EntregaConEstadoAnalisis(it, tieneAnalisis = it.id in idsConAnalisis) }
    }
}
