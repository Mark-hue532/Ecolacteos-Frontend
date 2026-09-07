package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.RecepcionPlantaRepository
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.RecepcionPlanta

/** `R-02` cuando se llega por id (desde `R-03`, no recién creada). */
class ObtenerDetalleRecepcionUseCase(private val repository: RecepcionPlantaRepository) {
    suspend operator fun invoke(id: String): ResultadoDominio<RecepcionPlanta> = repository.obtenerDetalle(id)
}
