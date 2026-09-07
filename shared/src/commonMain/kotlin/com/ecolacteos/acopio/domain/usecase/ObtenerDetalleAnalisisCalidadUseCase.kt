package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.domain.model.AnalisisCalidadDetalle

/** `C-04` (`MOBILE_SCREENS.md §6`) -- delgado a propósito, ver `AnalisisCalidadRepository.obtenerDetallePorRegistro`. */
class ObtenerDetalleAnalisisCalidadUseCase(private val repository: AnalisisCalidadRepository) {
    suspend operator fun invoke(registroAcopioId: String): AnalisisCalidadDetalle? = repository.obtenerDetallePorRegistro(registroAcopioId)
}
