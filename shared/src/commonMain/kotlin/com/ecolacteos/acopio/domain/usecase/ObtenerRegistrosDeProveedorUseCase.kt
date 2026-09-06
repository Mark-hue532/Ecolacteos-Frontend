package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia

/**
 * Puebla la lista "elegí el registro padre" cuando es ajeno (`§4.2` caso 3, `§5`) -- dispara la
 * población on-demand de `registro_acopio_cache`. Ver `RegistroAcopioRepository.obtenerRegistrosDeProveedor`
 * para el detalle de la degradación a cache sin conectividad.
 */
class ObtenerRegistrosDeProveedorUseCase(private val repository: RegistroAcopioRepository) {
    suspend operator fun invoke(proveedorId: String): List<RegistroAcopioReferencia> =
        repository.obtenerRegistrosDeProveedor(proveedorId)
}
