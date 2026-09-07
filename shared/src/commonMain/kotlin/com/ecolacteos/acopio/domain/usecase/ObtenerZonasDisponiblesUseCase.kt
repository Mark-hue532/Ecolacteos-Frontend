package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.CatalogoRepository
import kotlinx.coroutines.flow.first

/** Una zona seleccionable en `C-07` (`§6`, decisión 1 del checkpoint). */
data class ZonaOpcion(val id: String, val nombre: String)

/**
 * `C-07` (`PROMPT_FASE_08E.md §6`, decisión 1): el contrato no expone la zona del usuario (`DATA-016`), y
 * la heurística de `ObtenerZonaAsignadaUseCase` (`8A`) fue diseñada sobre `Unidad.responsableId` -- una
 * premisa del rol de campo (ACOPIADOR) que no vale para CALIDAD, que trabaja sobre varias zonas. La
 * respuesta correcta del documento es **un selector**, armado con las zonas distintas que ya trajo
 * `/sync/cambios` en `proveedor_cache` (`ProveedorPublicoResponse.zonaActualId`/`zonaActualNombre`,
 * `MOBILE_DATA_MAPPING.md §5.6`) -- confirmado que `ProveedorCache.sq` las persiste.
 */
class ObtenerZonasDisponiblesUseCase(private val catalogoRepository: CatalogoRepository) {
    suspend operator fun invoke(): List<ZonaOpcion> = catalogoRepository.observarProveedores().first()
        .mapNotNull { proveedor -> proveedor.zonaActualId?.let { id -> ZonaOpcion(id, proveedor.zonaActualNombre ?: id) } }
        .distinctBy { it.id }
        .sortedBy { it.nombre }
}
