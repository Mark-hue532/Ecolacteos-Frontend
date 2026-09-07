package com.ecolacteos.acopio.presentation.acopio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.data.remote.dto.Rol
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleRegistroAcopioUseCase
import com.ecolacteos.acopio.presentation.NO_DISPONIBLE
import com.ecolacteos.acopio.presentation.formateada
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val ESCALA_LITROS = 2
private const val ESCALA_GPS = 6

data class DetalleRegistroAcopioUiState(
    val cargando: Boolean = true,
    val encontrada: Boolean = true,
    val proveedorNombreTexto: String = "",
    val fechaCapturadoTexto: String = "",
    val litrosTexto: String = "",
    /** `null` -> se omite la fila entera (`§10.1` regla 3): un `0,0` es una coordenada real, no "sin dato". */
    val gpsLatTexto: String? = null,
    val gpsLngTexto: String? = null,
    val motivoObservacionTexto: String? = null,
    val sincronizadoTexto: String? = null,
    val puedeRegistrarCorreccion: Boolean = false,
)

sealed interface DetalleRegistroAcopioEvent {
    data object ReintentarPresionado : DetalleRegistroAcopioEvent
    data object RegistrarCorreccionPresionado : DetalleRegistroAcopioEvent
}

sealed interface DetalleRegistroAcopioEffect {
    /** `C-06` es de la Fase 8E -- el punto de entrada existe, el destino todavía no (`PROMPT_FASE_08A.md §2`). */
    data object CorreccionNoDisponibleTodavia : DetalleRegistroAcopioEffect
}

/**
 * `A-06 · Detalle de registro de acopio` (`MOBILE_SCREENS.md §5`, ONLINE+CACHE). `id` es el `server_id`
 * (viene de una fila de `A-05` que ya tenía uno) -- nunca el `uuidCliente`.
 */
class DetalleRegistroAcopioViewModel(
    private val id: String,
    private val obtenerDetalleRegistroAcopioUseCase: ObtenerDetalleRegistroAcopioUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
    private val gestorSesion: GestorSesion,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalleRegistroAcopioUiState())
    val uiState: StateFlow<DetalleRegistroAcopioUiState> = _uiState.asStateFlow()

    private val _effect = Channel<DetalleRegistroAcopioEffect>(Channel.BUFFERED)
    val effect: Flow<DetalleRegistroAcopioEffect> = _effect.receiveAsFlow()

    init {
        cargar()
    }

    fun onEvent(evento: DetalleRegistroAcopioEvent) {
        when (evento) {
            DetalleRegistroAcopioEvent.ReintentarPresionado -> cargar()
            DetalleRegistroAcopioEvent.RegistrarCorreccionPresionado ->
                viewModelScope.launch { _effect.send(DetalleRegistroAcopioEffect.CorreccionNoDisponibleTodavia) }
        }
    }

    private fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true)
        viewModelScope.launch {
            val detalle = obtenerDetalleRegistroAcopioUseCase(id)
            if (detalle == null) {
                _uiState.value = DetalleRegistroAcopioUiState(cargando = false, encontrada = false)
                return@launch
            }

            // NAME_MISMATCH (§5.2, trampa #9): motivoObservacionTexto y motivoObservacionId nunca se
            // confunden. Si el Repository no trajo el texto (fallback local propio), se resuelve el id
            // contra el catálogo -- mismo patrón que DetalleVentaViewModel con tipoQuesoNombre.
            val motivoTexto = detalle.motivoObservacionTexto
                ?: detalle.motivoObservacionId?.let { id ->
                    observarCatalogosUseCase.motivosObservacion().first().firstOrNull { it.id == id }?.descripcion
                }

            val rol = gestorSesion.sesionActual()?.rol

            _uiState.value = DetalleRegistroAcopioUiState(
                cargando = false,
                encontrada = true,
                proveedorNombreTexto = detalle.proveedorNombre ?: NO_DISPONIBLE,
                fechaCapturadoTexto = detalle.fechaHora.formateada(),
                litrosTexto = detalle.litros.aTextoConEscala(ESCALA_LITROS),
                gpsLatTexto = detalle.gpsLat?.aTextoConEscala(ESCALA_GPS),
                gpsLngTexto = detalle.gpsLng?.aTextoConEscala(ESCALA_GPS),
                motivoObservacionTexto = motivoTexto,
                sincronizadoTexto = detalle.sincronizadoEn?.formateada(),
                puedeRegistrarCorreccion = rol == Rol.CALIDAD,
            )
        }
    }
}
