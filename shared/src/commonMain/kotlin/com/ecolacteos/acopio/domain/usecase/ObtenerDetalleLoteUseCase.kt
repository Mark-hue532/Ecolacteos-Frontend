package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.LoteProduccionRepository
import com.ecolacteos.acopio.domain.model.LoteProduccionDetalle

/** `P-04` (`MOBILE_SCREENS.md §7`) -- delgado a propósito, ver `LoteProduccionRepository.obtenerDetalle`. */
class ObtenerDetalleLoteUseCase(private val repository: LoteProduccionRepository) {
    suspend operator fun invoke(id: String): LoteProduccionDetalle? = repository.obtenerDetalle(id)
}
