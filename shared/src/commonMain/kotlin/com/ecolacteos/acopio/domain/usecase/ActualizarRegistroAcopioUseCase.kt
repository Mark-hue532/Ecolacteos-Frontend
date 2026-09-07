package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.NuevoRegistroAcopio
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository

/** `S-05` "editar y reintentar" (`§4`) -- delgado a propósito, ver `RegistroAcopioRepository.actualizar`. */
class ActualizarRegistroAcopioUseCase(private val repository: RegistroAcopioRepository) {
    suspend operator fun invoke(uuidCliente: String, datos: NuevoRegistroAcopio): Boolean =
        repository.actualizar(uuidCliente, datos)
}
