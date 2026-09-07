package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.NuevaVenta
import com.ecolacteos.acopio.data.repository.VentaRepository

/** `S-05` "editar y reintentar" (`§4`) -- delgado a propósito, ver `VentaRepository.actualizar`. */
class ActualizarVentaUseCase(private val repository: VentaRepository) {
    suspend operator fun invoke(uuidCliente: String, datos: NuevaVenta): Boolean = repository.actualizar(uuidCliente, datos)
}
