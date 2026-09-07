package com.ecolacteos.acopio.presentation.comun

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Marca en memoria de qué comunicados se confirmaron esta sesión (`A-07` → `S-06`, `MOBILE_SCREENS.md §5`:
 * "la confirmación queda marcada en la lista de S-06"). Deliberadamente efímera -- no hay tabla local para
 * `ComunicadoConfirmacion` (`MOBILE_ARCHITECTURE.md §11.3`, `DATA-005`) ni un endpoint de lectura para el
 * móvil (`GET /api/comunicados/{id}/confirmaciones` es WEB/ADMIN, `MOBILE_DATA_MAPPING.md §11`): la app no
 * tiene forma de saber si ya se confirmó, así que no hay nada "verdadero" que persistir. Decisión 5 del
 * checkpoint de la Fase 8B -- no sobrevive a un reinicio del proceso, y eso no tiene arreglo limpio hoy.
 *
 * Un solo `single` de Koin compartido entre `ComunicadosViewModel` (lee) y `ConfirmarComunicadoViewModel`
 * (escribe) -- evita inventar un mecanismo de "resultado de navegación" para algo que ya se acepta como
 * best-effort.
 */
class ConfirmacionesRecientesEnMemoria {
    private val _confirmados = MutableStateFlow<Set<String>>(emptySet())
    val confirmados: StateFlow<Set<String>> = _confirmados.asStateFlow()

    fun marcarConfirmado(comunicadoId: String) {
        _confirmados.update { it + comunicadoId }
    }
}
