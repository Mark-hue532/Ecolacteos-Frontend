package com.ecolacteos.acopio.presentation.calidad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleAnalisisCalidadUseCase
import com.ecolacteos.acopio.presentation.formateada
import com.ecolacteos.acopio.presentation.formateadoConEscala
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val ESCALA_LABORATORIO = 2

/**
 * `DetalleAnalisisUiState` (`C-04`, `MOBILE_SCREENS.md §6`). Los 6 parámetros nulos se omiten -- `null` en
 * el campo, nunca `"0.00"` (`§10.1` regla 3). [resultadoTexto] es `null` mientras el servidor no confirmó
 * (degradado a `analisis_calidad_local`, ver `AnalisisCalidadDetalle.resultado`); cuando no es nulo, es el
 * `.name` del enum tal cual -- incluido `"UNKNOWN"` -- porque `EnumConReservaSerializer` (`§1.6`) colapsa
 * cualquier valor no reconocido a esa reserva antes de llegar acá: no queda un texto "original" que
 * reproducir, y `.name` es la forma más honesta de "mostrarlo tal cual llegó" sin inventar una traducción
 * que oculte que el valor no es uno de los conocidos.
 */
data class DetalleAnalisisUiState(
    val cargando: Boolean = true,
    val encontrado: Boolean = true,
    val folioMuestraTexto: String = "",
    val aguaTexto: String? = null,
    val proteinaTexto: String? = null,
    val lactosaTexto: String? = null,
    val densidadTexto: String? = null,
    val temperaturaTexto: String? = null,
    val phTexto: String? = null,
    val aguaAnadidaTexto: String = "No",
    val resultadoTexto: String? = null,
    val creadoTexto: String = "",
    val puedeRegistrarCorreccion: Boolean = true,
)

sealed interface DetalleAnalisisEvent {
    data object ReintentarPresionado : DetalleAnalisisEvent
    data object RegistrarCorreccionPresionado : DetalleAnalisisEvent
}

sealed interface DetalleAnalisisEffect {
    /** `C-06` es de la Fase 8E -- el punto de entrada existe, el destino todavía no (mismo criterio que `A-06`). */
    data object CorreccionNoDisponibleTodavia : DetalleAnalisisEffect
}

/** `C-04 · Detalle de análisis` (Fase 8C, `MOBILE_SCREENS.md §6`, ONLINE+CACHE). `registroAcopioId` viene de `C-01`. */
class DetalleAnalisisCalidadViewModel(
    private val registroAcopioId: String,
    private val obtenerDetalleAnalisisCalidadUseCase: ObtenerDetalleAnalisisCalidadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalleAnalisisUiState())
    val uiState: StateFlow<DetalleAnalisisUiState> = _uiState.asStateFlow()

    private val _effect = Channel<DetalleAnalisisEffect>(Channel.BUFFERED)
    val effect: Flow<DetalleAnalisisEffect> = _effect.receiveAsFlow()

    init {
        cargar()
    }

    fun onEvent(evento: DetalleAnalisisEvent) {
        when (evento) {
            DetalleAnalisisEvent.ReintentarPresionado -> cargar()
            DetalleAnalisisEvent.RegistrarCorreccionPresionado ->
                viewModelScope.launch { _effect.send(DetalleAnalisisEffect.CorreccionNoDisponibleTodavia) }
        }
    }

    private fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true)
        viewModelScope.launch {
            val detalle = obtenerDetalleAnalisisCalidadUseCase(registroAcopioId)
            if (detalle == null) {
                _uiState.value = DetalleAnalisisUiState(cargando = false, encontrado = false)
                return@launch
            }

            _uiState.value = DetalleAnalisisUiState(
                cargando = false,
                encontrado = true,
                folioMuestraTexto = detalle.folioMuestra,
                aguaTexto = detalle.agua.formateadoConEscala(ESCALA_LABORATORIO),
                proteinaTexto = detalle.proteina.formateadoConEscala(ESCALA_LABORATORIO),
                lactosaTexto = detalle.lactosa.formateadoConEscala(ESCALA_LABORATORIO),
                densidadTexto = detalle.densidad.formateadoConEscala(ESCALA_LABORATORIO),
                temperaturaTexto = detalle.temperatura.formateadoConEscala(ESCALA_LABORATORIO),
                phTexto = detalle.ph.formateadoConEscala(ESCALA_LABORATORIO),
                aguaAnadidaTexto = if (detalle.aguaAnadida) "Sí" else "No",
                resultadoTexto = detalle.resultado?.name,
                creadoTexto = detalle.creadoEn.formateada(),
            )
        }
    }
}
