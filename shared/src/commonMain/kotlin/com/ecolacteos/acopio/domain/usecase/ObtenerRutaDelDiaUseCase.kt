package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.CatalogoRepository
import com.ecolacteos.acopio.domain.model.RutaProveedorOrden

/** Bajo demanda (`§18.5`, `§5`) -- ver `CatalogoRepository.obtenerRutaDelDia` para la degradación a cache. */
class ObtenerRutaDelDiaUseCase(private val repository: CatalogoRepository) {
    suspend operator fun invoke(zonaId: String): List<RutaProveedorOrden> = repository.obtenerRutaDelDia(zonaId)
}
