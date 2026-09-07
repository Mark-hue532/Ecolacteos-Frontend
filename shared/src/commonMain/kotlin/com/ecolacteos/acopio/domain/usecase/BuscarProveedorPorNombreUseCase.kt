package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.CatalogoRepository
import com.ecolacteos.acopio.domain.model.Proveedor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * `A-03` (`MOBILE_SCREENS.md §5`): búsqueda por nombre sobre `proveedor_cache`, 100% local -- "OFFLINE
 * REAL". `query` en blanco devuelve el catálogo completo (mismo criterio que un buscador vacío = sin
 * filtrar). Reactivo (no un `suspend` de una sola lectura) para que la lista se actualice sola si
 * `proveedor_cache` cambia mientras la pantalla está abierta (ej. un sync que termina en ese momento).
 */
class BuscarProveedorPorNombreUseCase(private val repository: CatalogoRepository) {
    operator fun invoke(query: String): Flow<List<Proveedor>> = repository.observarProveedores().map { proveedores ->
        if (query.isBlank()) proveedores else proveedores.filter { it.nombre.contains(query, ignoreCase = true) }
    }
}
