package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.VentaRepository
import com.ecolacteos.acopio.domain.model.VentaDetalle

/** `V-03` (`MOBILE_SCREENS.md §8`) -- ver `VentaRepository.obtenerDetalle` para la estrategia local+remoto. */
class ObtenerDetalleVentaUseCase(private val repository: VentaRepository) {
    suspend operator fun invoke(uuidCliente: String): VentaDetalle? = repository.obtenerDetalle(uuidCliente)
}
