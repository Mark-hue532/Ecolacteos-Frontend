package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.PagoRepository
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.Pago

/** `R-04` (`MOBILE_SCREENS.md §9`) -- solo lectura, el móvil no genera pagos. */
class ObtenerPagosDeProveedorUseCase(private val repository: PagoRepository) {
    suspend operator fun invoke(proveedorId: String): ResultadoDominio<List<Pago>> = repository.obtenerPorProveedor(proveedorId)
}
