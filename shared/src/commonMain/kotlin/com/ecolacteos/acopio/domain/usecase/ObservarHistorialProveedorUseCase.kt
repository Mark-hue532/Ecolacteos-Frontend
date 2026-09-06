package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.ItemHistorialRegistroAcopio
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import kotlinx.coroutines.flow.Flow

/** Lectura reactiva del historial de un proveedor (`§16.4`) -- propios + ajenos combinados. */
class ObservarHistorialProveedorUseCase(private val repository: RegistroAcopioRepository) {
    operator fun invoke(proveedorId: String): Flow<List<ItemHistorialRegistroAcopio>> =
        repository.observarHistorialProveedor(proveedorId)
}
