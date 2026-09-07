package com.ecolacteos.acopio.presentation.recepcion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleRecepcionUseCase
import com.ecolacteos.acopio.presentation.formateada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val ESCALA_LITROS = 2

/**
 * `ResultadoConciliacionUiState` (`R-02`, `MOBILE_SCREENS.md §9`). [litrosRegistradosAcopioTexto] `null`
 * significa "sin registros de acopio para esta unidad y fecha" -- **nunca** `"0.00"` (`§10.1` regla 3,
 * trampa #4): un `SUM()` sobre cero filas da `NULL`, no cero.
 */
data class ResultadoConciliacionUiState(
    val cargando: Boolean = true,
    val encontrado: Boolean = true,
    val fechaTexto: String = "",
    val turnoTexto: String = "",
    val litrosCampoTexto: String = "",
    val litrosPlantaTexto: String = "",
    val diferenciaPctTexto: String = "",
    val estadoTexto: String = "",
    val litrosRegistradosAcopioTexto: String? = null,
    val mensajeError: String? = null,
)

sealed interface ResultadoConciliacionEvent {
    data object ReintentarPresionado : ResultadoConciliacionEvent
}

/** `R-02 · Resultado de conciliación` (Fase 8E, `MOBILE_SCREENS.md §9`) -- ONLINE-ONLY, sin degradación a cache: no hay tabla local (`§11.3`). */
class ResultadoConciliacionViewModel(
    private val id: String,
    private val obtenerDetalleRecepcionUseCase: ObtenerDetalleRecepcionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultadoConciliacionUiState())
    val uiState: StateFlow<ResultadoConciliacionUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun onEvent(evento: ResultadoConciliacionEvent) {
        when (evento) {
            ResultadoConciliacionEvent.ReintentarPresionado -> cargar()
        }
    }

    private fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true, mensajeError = null)
        viewModelScope.launch {
            when (val resultado = obtenerDetalleRecepcionUseCase(id)) {
                is ResultadoDominio.Exito -> {
                    val recepcion = resultado.datos
                    _uiState.value = ResultadoConciliacionUiState(
                        cargando = false,
                        encontrado = true,
                        fechaTexto = recepcion.fecha.formateada(),
                        turnoTexto = recepcion.turno,
                        litrosCampoTexto = recepcion.litrosCampo.aTextoConEscala(ESCALA_LITROS),
                        litrosPlantaTexto = recepcion.litrosPlanta.aTextoConEscala(ESCALA_LITROS),
                        diferenciaPctTexto = recepcion.diferenciaPct.aTextoConEscala(ESCALA_LITROS),
                        estadoTexto = recepcion.estado.name,
                        litrosRegistradosAcopioTexto = recepcion.litrosRegistradosAcopio?.aTextoConEscala(ESCALA_LITROS),
                    )
                }
                is ResultadoDominio.Error -> _uiState.value =
                    ResultadoConciliacionUiState(cargando = false, encontrado = false, mensajeError = resultado.error.mensaje)
            }
        }
    }
}
