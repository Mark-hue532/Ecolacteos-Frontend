package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.repository.ItemHistorialRegistroAcopio
import com.ecolacteos.acopio.data.repository.ReferenciaRegistroAcopio
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime

/**
 * Los tres casos de elegibilidad de `C-02` (`MOBILE_SCREENS.md §6`, `MOBILE_ARCHITECTURE.md §18.1`).
 * Refina [ItemHistorialRegistroAcopio] (Fase 6, ya deduplicado por `DATA-013`) con la única distinción que
 * le falta para esta pantalla: un [Propio][ItemHistorialRegistroAcopio.Propio] es analizable siempre --
 * pero si todavía no tiene `serverId`, el hijo va a nacer `PENDING_DEPENDENCY` (`§18.1` mecanismo 1), y eso
 * hay que decírselo al usuario **antes** de que elija (`DATA-003`), no después de guardar.
 *
 * `8D` (`P-02`) reutiliza este mismo clasificador tal cual -- selección múltiple sobre las mismas tres
 * categorías, ver `PROMPT_FASE_08C.md §6`.
 */
sealed interface PadreRegistroAcopioElegible {
    val fechaHora: LocalDateTime
    val litros: Decimal

    /** Caso 1: ajena ya descargada (`registro_acopio_cache`, tiene `id` de servidor). Siempre resoluble. */
    data class AjenoDisponible(val referencia: RegistroAcopioReferencia) : PadreRegistroAcopioElegible {
        override val fechaHora get() = referencia.fechaHora
        override val litros get() = referencia.litros
    }

    /** Caso 2: propia y ya sincronizada (`server_id` presente). Resoluble sin pasar por espera. */
    data class PropioSincronizado(val registro: RegistroAcopio) : PadreRegistroAcopioElegible {
        override val fechaHora get() = registro.fechaHora
        override val litros get() = registro.litros
    }

    /** Caso 3: propia sin sincronizar todavía. Seleccionable, pero el hijo nace `PENDING_DEPENDENCY`. */
    data class PropioPendienteDeSync(val registro: RegistroAcopio) : PadreRegistroAcopioElegible {
        override val fechaHora get() = registro.fechaHora
        override val litros get() = registro.litros
    }
}

/**
 * La referencia que [CrearAnalisisCalidadUseCase]/`CrearLoteProduccionUseCase` necesitan para resolver el
 * padre (`ResolutorPadreRegistroAcopio`, Fase 6) -- casos 2 y 3 resuelven igual (por `uuidCliente`; el
 * resolutor decide él mismo si ya hay `serverId` o si hay que diferir), la única diferencia entre ambos es
 * el aviso que ve el usuario **antes** de llegar acá.
 */
fun PadreRegistroAcopioElegible.aReferenciaPadre(): ReferenciaRegistroAcopio = when (this) {
    is PadreRegistroAcopioElegible.AjenoDisponible -> ReferenciaRegistroAcopio.Ajeno(referencia.id)
    is PadreRegistroAcopioElegible.PropioSincronizado -> ReferenciaRegistroAcopio.Propio(registro.uuidCliente)
    is PadreRegistroAcopioElegible.PropioPendienteDeSync -> ReferenciaRegistroAcopio.Propio(registro.uuidCliente)
}

/**
 * `C-02` (Fase 8C, ★ central) -- clasifica el historial combinado de un proveedor en los tres casos de
 * arriba. No reimplementa la resolución de padre (`PROMPT_FASE_08C.md §0`): solo lee el mismo historial
 * reactivo que ya arma `RegistroAcopioRepository.observarHistorialProveedor` (deduplicado por `DATA-013`)
 * y le agrega la distinción sincronizado/pendiente que la UI necesita mostrar.
 */
class ClasificarPadresRegistroAcopioUseCase(private val repository: RegistroAcopioRepository) {
    operator fun invoke(proveedorId: String): Flow<List<PadreRegistroAcopioElegible>> =
        repository.observarHistorialProveedor(proveedorId).map { items -> items.map { it.aElegible() } }
}

private fun ItemHistorialRegistroAcopio.aElegible(): PadreRegistroAcopioElegible = when (this) {
    is ItemHistorialRegistroAcopio.Ajeno -> PadreRegistroAcopioElegible.AjenoDisponible(referencia)
    is ItemHistorialRegistroAcopio.Propio ->
        if (registro.serverId != null) {
            PadreRegistroAcopioElegible.PropioSincronizado(registro)
        } else {
            PadreRegistroAcopioElegible.PropioPendienteDeSync(registro)
        }
}
