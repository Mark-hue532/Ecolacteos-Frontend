package com.ecolacteos.acopio.presentation.calidad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.data.repository.ResultadoScoreConfianza
import com.ecolacteos.acopio.domain.usecase.ObtenerScoreConfianzaUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val ESCALA_SCORE = 2

data class ScoreConfianzaUiState(
    val cargando: Boolean = true,
    val encontrado: Boolean = false,
    val scoreTexto: String = "",
    val componenteCalidadTexto: String = "",
    val componenteRegularidadTexto: String = "",
    val componenteAnomaliasTexto: String = "",
    val mensajeError: String? = null,
)

sealed interface ScoreConfianzaEvent {
    data object ReintentarPresionado : ScoreConfianzaEvent
}

/**
 * `C-08 · Score de confianza del proveedor` (Fase 8E, `MOBILE_SCREENS.md §6`) -- ONLINE-ONLY. Un `404` no
 * es un fallo (trampa #7): significa "este proveedor todavía no tiene histórico" -- estado vacío, nunca
 * el mapeo genérico de error.
 */
class ScoreConfianzaViewModel(
    private val proveedorId: String,
    private val obtenerScoreConfianzaUseCase: ObtenerScoreConfianzaUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScoreConfianzaUiState())
    val uiState: StateFlow<ScoreConfianzaUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun onEvent(evento: ScoreConfianzaEvent) {
        when (evento) {
            ScoreConfianzaEvent.ReintentarPresionado -> cargar()
        }
    }

    private fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true, mensajeError = null)
        viewModelScope.launch {
            when (val resultado = obtenerScoreConfianzaUseCase(proveedorId)) {
                is ResultadoScoreConfianza.Encontrado -> _uiState.value = ScoreConfianzaUiState(
                    cargando = false,
                    encontrado = true,
                    scoreTexto = resultado.score.score.aTextoConEscala(ESCALA_SCORE),
                    componenteCalidadTexto = resultado.score.componenteCalidad.aTextoConEscala(ESCALA_SCORE),
                    componenteRegularidadTexto = resultado.score.componenteRegularidad.aTextoConEscala(ESCALA_SCORE),
                    componenteAnomaliasTexto = resultado.score.componenteAnomalias.aTextoConEscala(ESCALA_SCORE),
                )
                ResultadoScoreConfianza.SinHistorico -> _uiState.value = ScoreConfianzaUiState(cargando = false, encontrado = false)
                is ResultadoScoreConfianza.Error -> _uiState.value =
                    ScoreConfianzaUiState(cargando = false, encontrado = false, mensajeError = resultado.error.mensaje)
            }
        }
    }
}
