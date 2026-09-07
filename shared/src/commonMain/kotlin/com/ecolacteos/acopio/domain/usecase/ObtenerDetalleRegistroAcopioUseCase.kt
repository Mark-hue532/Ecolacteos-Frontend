package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.domain.model.RegistroAcopioDetalle

/** `A-06` (`MOBILE_SCREENS.md §5`) -- delgado a propósito, ver `RegistroAcopioRepository.obtenerDetalle`. */
class ObtenerDetalleRegistroAcopioUseCase(private val repository: RegistroAcopioRepository) {
    suspend operator fun invoke(id: String): RegistroAcopioDetalle? = repository.obtenerDetalle(id)
}
