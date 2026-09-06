package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.CatalogoRepository
import com.ecolacteos.acopio.domain.model.Comunicado
import com.ecolacteos.acopio.domain.model.MotivoObservacion
import com.ecolacteos.acopio.domain.model.PrecioLitroVigente
import com.ecolacteos.acopio.domain.model.PrediccionProveedor
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.model.TipoQueso
import com.ecolacteos.acopio.domain.model.Unidad
import kotlinx.coroutines.flow.Flow

/**
 * Lectura reactiva de catálogos (`§5`) -- un método por catálogo en vez de un solo `Flow` combinado a
 * propósito: son 6 tipos completamente distintos y una futura pantalla normalmente solo necesita uno o
 * dos a la vez (ej. el dropdown de `TipoQueso` en el formulario de Venta no necesita `Comunicado`).
 * Combinar los 6 en un solo objeto obligaría a recomponer la UI entera cada vez que cualquiera cambie.
 */
class ObservarCatalogosUseCase(private val repository: CatalogoRepository) {
    fun proveedores(): Flow<List<Proveedor>> = repository.observarProveedores()
    fun unidades(): Flow<List<Unidad>> = repository.observarUnidades()
    fun motivosObservacion(): Flow<List<MotivoObservacion>> = repository.observarMotivosObservacion()
    fun tiposQueso(): Flow<List<TipoQueso>> = repository.observarTiposQueso()
    fun comunicados(): Flow<List<Comunicado>> = repository.observarComunicados()
    fun predicciones(): Flow<List<PrediccionProveedor>> = repository.observarPredicciones()
    fun precioVigente(): PrecioLitroVigente? = repository.obtenerPrecioVigente()

    /** Refresco manual (futuro "pull to refresh") -- delega en el Sync Engine, `§4.3`. */
    fun refrescar() = repository.refrescar()
}
