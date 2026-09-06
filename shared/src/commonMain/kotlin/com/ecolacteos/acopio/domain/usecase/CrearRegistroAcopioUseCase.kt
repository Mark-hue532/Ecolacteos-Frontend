package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.NuevoRegistroAcopio
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository

/** Delgado a propósito (`§5`): no valida nada de formulario (eso es `MOBILE_SCREENS.md`/Fase 7), solo llama al Repository. */
class CrearRegistroAcopioUseCase(private val repository: RegistroAcopioRepository) {
    suspend operator fun invoke(datos: NuevoRegistroAcopio): String = repository.crear(datos)
}
