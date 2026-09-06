package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.data.repository.NuevoAnalisisCalidad
import com.ecolacteos.acopio.data.repository.ResultadoCrearHijo

/** Recibe la referencia al padre ya resuelta por la UI (propio o ajeno-elegido-de-lista, `§5`). */
class CrearAnalisisCalidadUseCase(private val repository: AnalisisCalidadRepository) {
    suspend operator fun invoke(datos: NuevoAnalisisCalidad): ResultadoCrearHijo = repository.crear(datos)
}
