package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.RecepcionPlantaRepository
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.RecepcionPlanta

/** `R-03` y la búsqueda de `R-02b` (`PROMPT_FASE_08E.md §7`) -- un solo método, dos consumidores (trampa #12). */
class BuscarRecepcionesUseCase(private val repository: RecepcionPlantaRepository) {
    suspend operator fun invoke(unidadId: String? = null): ResultadoDominio<List<RecepcionPlanta>> = repository.buscarPorUnidad(unidadId)
}
