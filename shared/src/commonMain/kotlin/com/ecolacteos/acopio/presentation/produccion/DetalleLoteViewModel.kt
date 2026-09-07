package com.ecolacteos.acopio.presentation.produccion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleLoteUseCase
import com.ecolacteos.acopio.presentation.NO_DISPONIBLE
import com.ecolacteos.acopio.presentation.formateada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ESCALA_LITROS = 2
private const val ESCALA_RENDIMIENTO = 2

/**
 * `DetalleLoteUiState` (`C-04`, `MOBILE_SCREENS.md §7`). [rendimientoRealTexto] `null` = "No calculado" --
 * **nunca** se dibuja una comparación cuando es nulo (`§6`, trampa #7): [hayComparacion] es la única señal
 * que el `@Composable` necesita para decidir, nunca infiere a partir de un texto.
 */
data class DetalleLoteUiState(
    val cargando: Boolean = true,
    val encontrado: Boolean = true,
    val fechaTexto: String = "",
    val tipoQuesoNombreTexto: String = NO_DISPONIBLE,
    val litrosUsadosTexto: String = "",
    val unidadesObtenidas: Int = 0,
    val rendimientoEsperadoTexto: String = NO_DISPONIBLE,
    val rendimientoRealTexto: String? = null,
    val hayComparacion: Boolean = false,
)

sealed interface DetalleLoteEvent {
    data object ReintentarPresionado : DetalleLoteEvent
}

/** `P-04 · Detalle de lote` (Fase 8D, `MOBILE_SCREENS.md §7`, ONLINE+CACHE). `id` es el `server_id`. */
class DetalleLoteViewModel(
    private val id: String,
    private val obtenerDetalleLoteUseCase: ObtenerDetalleLoteUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalleLoteUiState())
    val uiState: StateFlow<DetalleLoteUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun onEvent(evento: DetalleLoteEvent) {
        when (evento) {
            DetalleLoteEvent.ReintentarPresionado -> cargar()
        }
    }

    private fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true)
        viewModelScope.launch {
            val detalle = obtenerDetalleLoteUseCase(id)
            if (detalle == null) {
                _uiState.value = DetalleLoteUiState(cargando = false, encontrado = false)
                return@launch
            }

            // NAME_MISMATCH (mismo criterio que RegistroAcopioDetalle/motivoObservacion): el Response trae
            // el nombre del tipo de queso pero no su id; lo local trae el id pero no el nombre. Si degradó
            // a lo local, se resuelve nombre + esperado contra el catálogo -- nunca se inventa.
            val tipoQuesoCache = detalle.tipoQuesoId?.let { id ->
                observarCatalogosUseCase.tiposQueso().first().firstOrNull { it.id == id }
            }
            val rendimientoEsperado = detalle.rendimientoEsperadoPct ?: tipoQuesoCache?.rendimientoEsperadoPct

            _uiState.value = DetalleLoteUiState(
                cargando = false,
                encontrado = true,
                fechaTexto = detalle.fecha.formateada(),
                tipoQuesoNombreTexto = detalle.tipoQuesoNombre ?: tipoQuesoCache?.nombre ?: NO_DISPONIBLE,
                litrosUsadosTexto = detalle.litrosUsados.aTextoConEscala(ESCALA_LITROS),
                unidadesObtenidas = detalle.unidadesObtenidas,
                rendimientoEsperadoTexto = rendimientoEsperado?.aTextoConEscala(ESCALA_RENDIMIENTO) ?: NO_DISPONIBLE,
                rendimientoRealTexto = detalle.rendimientoPct?.aTextoConEscala(ESCALA_RENDIMIENTO),
                hayComparacion = detalle.rendimientoPct != null,
            )
        }
    }
}
