package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.repository.LoteProduccionRepository
import com.ecolacteos.acopio.domain.model.LoteProduccion
import com.ecolacteos.acopio.domain.model.TipoQueso
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Una fila de `P-01` -- el lote con el nombre de su tipo de queso ya resuelto (`tipo_queso_id` no se
 * muestra crudo, `§3.3`) y [rendimientoEsperadoPct] del catálogo. **Sin `rendimientoPct` real** a
 * propósito (decisión 2 del checkpoint): ese campo solo lo confirma el servidor
 * (`GET /api/lotes-produccion/{id}`, `P-04`) y `P-01` es 100% offline (`MOBILE_SCREENS.md §7`: "Modo
 * offline OK") -- mostrar el esperado, claramente etiquetado como tal, evita inventar un rendimiento real
 * que nadie confirmó (trampa #6) sin dejar la fila vacía de información útil.
 */
data class LoteConTipoQueso(val lote: LoteProduccion, val tipoQuesoNombre: String, val rendimientoEsperadoPct: Decimal)

/** `P-01 · Home producción` (Fase 8D, `MOBILE_SCREENS.md §7`) -- `lote_produccion_local` + `tipo_queso_cache`. */
class ObservarLotesRecientesUseCase(
    private val loteProduccionRepository: LoteProduccionRepository,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
) {
    operator fun invoke(): Flow<List<LoteConTipoQueso>> = combine(
        loteProduccionRepository.observarPendientes(),
        observarCatalogosUseCase.tiposQueso(),
    ) { lotes, tiposQueso ->
        val tipoQuesoPorId = tiposQueso.associateBy(TipoQueso::id)
        lotes.mapNotNull { lote ->
            val tipoQueso = tipoQuesoPorId[lote.tipoQuesoId] ?: return@mapNotNull null
            LoteConTipoQueso(lote, tipoQueso.nombre, tipoQueso.rendimientoEsperadoPct)
        }
    }
}
