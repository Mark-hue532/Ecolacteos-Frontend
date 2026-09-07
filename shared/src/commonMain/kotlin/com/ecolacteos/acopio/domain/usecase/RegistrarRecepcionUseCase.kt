package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.NuevaRecepcionPlanta
import com.ecolacteos.acopio.data.repository.RecepcionPlantaRepository
import com.ecolacteos.acopio.data.repository.ResultadoRegistrarRecepcion

/** `R-01` (`MOBILE_SCREENS.md §9`) -- delgado, distingue el 409 en [ResultadoRegistrarRecepcion] (`R-02b`). */
class RegistrarRecepcionUseCase(private val repository: RecepcionPlantaRepository) {
    suspend operator fun invoke(datos: NuevaRecepcionPlanta): ResultadoRegistrarRecepcion = repository.registrar(datos)
}
