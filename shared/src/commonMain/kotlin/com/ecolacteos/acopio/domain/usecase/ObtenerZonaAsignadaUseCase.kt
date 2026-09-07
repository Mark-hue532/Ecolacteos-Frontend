package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.CatalogoRepository
import com.ecolacteos.acopio.domain.GestorSesion
import kotlinx.coroutines.flow.first

/**
 * Heurística aprobada en el checkpoint de la Fase 8A (hallazgo `DATA-016`): el contrato no expone la
 * `zonaId` del ACOPIADOR autenticado en ningún lado verificado -- ni el JWT (`sub`/`rol`/`usuarioId`,
 * `MOBILE_ARCHITECTURE.md §4`) ni un endpoint tipo `/api/usuarios/me`, y `GET /api/zonas/{zonaId}/ruta`
 * (`A-01`) la exige como path param.
 *
 * Se deriva de `unidad_cache`: la o las `Unidad` cuyo `responsableId` coincide con el usuario de la sesión
 * activa. Si hay **exactamente una** zona distinta entre ellas, se usa esa. Si hay cero (el usuario no es
 * responsable de ninguna unidad) o más de una (ambigüedad real, sin forma confiable de elegir), se
 * devuelve `null` -- `A-01` lo trata como "no se pudo determinar tu zona todavía", nunca inventa cuál
 * mostrar (`CLAUDE.md §6`: "preguntá antes de asumir").
 */
class ObtenerZonaAsignadaUseCase(
    private val catalogoRepository: CatalogoRepository,
    private val gestorSesion: GestorSesion,
) {
    suspend operator fun invoke(): String? {
        val usuarioId = gestorSesion.sesionActual()?.usuarioId ?: return null
        val zonas = catalogoRepository.observarUnidades().first()
            .filter { it.responsableId == usuarioId }
            .mapNotNull { it.zonaId }
            .distinct()
        return zonas.singleOrNull()
    }
}
