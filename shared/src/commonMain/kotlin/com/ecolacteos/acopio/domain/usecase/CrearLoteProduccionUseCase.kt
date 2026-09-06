package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.LoteProduccionRepository
import com.ecolacteos.acopio.data.repository.NuevoLoteProduccion
import com.ecolacteos.acopio.data.repository.ResultadoCrearHijo

/** Igual que [CrearAnalisisCalidadUseCase], con lista de padres (`§5`). */
class CrearLoteProduccionUseCase(private val repository: LoteProduccionRepository) {
    suspend operator fun invoke(datos: NuevoLoteProduccion): ResultadoCrearHijo = repository.crear(datos)
}
