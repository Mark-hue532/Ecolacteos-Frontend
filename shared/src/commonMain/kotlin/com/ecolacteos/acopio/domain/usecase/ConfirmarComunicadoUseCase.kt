package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.ComunicadoConfirmacionRepository
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.ComunicadoConfirmacion

/** Online-only (`§18.2`, `§5`) -- ver `ComunicadoConfirmacionRepository`, sin cola propia (trampa #9). */
class ConfirmarComunicadoUseCase(private val repository: ComunicadoConfirmacionRepository) {
    suspend operator fun invoke(comunicadoId: String, proveedorId: String): ResultadoDominio<ComunicadoConfirmacion> =
        repository.confirmar(comunicadoId, proveedorId)
}
