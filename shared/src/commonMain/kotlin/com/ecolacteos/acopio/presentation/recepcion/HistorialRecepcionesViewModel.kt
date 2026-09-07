package com.ecolacteos.acopio.presentation.recepcion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.RecepcionPlanta
import com.ecolacteos.acopio.domain.model.Unidad
import com.ecolacteos.acopio.domain.usecase.BuscarRecepcionesUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.presentation.formateada
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ESCALA_LITROS = 2

data class ItemRecepcionUiState(val id: String, val fechaTexto: String, val turnoTexto: String, val litrosCampoTexto: String, val estadoTexto: String)

data class HistorialRecepcionesUiState(
    val unidades: List<Unidad> = emptyList(),
    val unidadFiltro: Unidad? = null,
    val cargando: Boolean = true,
    val items: List<ItemRecepcionUiState> = emptyList(),
    val mensajeError: String? = null,
) {
    val vacio: Boolean get() = !cargando && items.isEmpty() && mensajeError == null
}

sealed interface HistorialRecepcionesEvent {
    data class FiltroUnidadCambio(val unidad: Unidad?) : HistorialRecepcionesEvent
    data class ItemSeleccionado(val id: String) : HistorialRecepcionesEvent
    data object VerPagosPresionado : HistorialRecepcionesEvent
}

sealed interface HistorialRecepcionesEffect {
    data class NavegarADetalle(val id: String) : HistorialRecepcionesEffect
    data object NavegarABuscarProveedor : HistorialRecepcionesEffect
}

/**
 * `R-03 · Historial de recepciones` (Fase 8E, `MOBILE_SCREENS.md §9`) -- ONLINE + CACHE opcional (decisión
 * 4 del checkpoint: sin tabla local real, `§11.3` lo confirma -- "cache opcional" queda en el nombre del
 * modo, no en una tabla nueva). Reusa `BuscarRecepcionesUseCase`, el mismo método que la búsqueda de
 * `R-02b` (trampa #12, un solo método).
 */
class HistorialRecepcionesViewModel(
    private val buscarRecepcionesUseCase: BuscarRecepcionesUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistorialRecepcionesUiState())
    val uiState: StateFlow<HistorialRecepcionesUiState> = _uiState.asStateFlow()

    private val _effect = Channel<HistorialRecepcionesEffect>(Channel.BUFFERED)
    val effect: Flow<HistorialRecepcionesEffect> = _effect.receiveAsFlow()

    init {
        observarCatalogosUseCase.unidades()
            .onEach { unidades -> _uiState.update { it.copy(unidades = unidades) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { buscar(null) }
    }

    fun onEvent(evento: HistorialRecepcionesEvent) {
        when (evento) {
            is HistorialRecepcionesEvent.FiltroUnidadCambio -> {
                _uiState.update { it.copy(unidadFiltro = evento.unidad) }
                viewModelScope.launch { buscar(evento.unidad?.id) }
            }
            is HistorialRecepcionesEvent.ItemSeleccionado ->
                viewModelScope.launch { _effect.send(HistorialRecepcionesEffect.NavegarADetalle(evento.id)) }
            HistorialRecepcionesEvent.VerPagosPresionado ->
                viewModelScope.launch { _effect.send(HistorialRecepcionesEffect.NavegarABuscarProveedor) }
        }
    }

    private suspend fun buscar(unidadId: String?) {
        _uiState.update { it.copy(cargando = true, mensajeError = null) }
        when (val resultado = buscarRecepcionesUseCase(unidadId)) {
            is ResultadoDominio.Exito -> _uiState.update {
                it.copy(cargando = false, items = resultado.datos.sortedByDescending { r -> r.fecha }.map { r -> r.aItemUi() })
            }
            is ResultadoDominio.Error -> _uiState.update { it.copy(cargando = false, items = emptyList(), mensajeError = resultado.error.mensaje) }
        }
    }

    private fun RecepcionPlanta.aItemUi(): ItemRecepcionUiState = ItemRecepcionUiState(
        id = id,
        fechaTexto = fecha.formateada(),
        turnoTexto = turno,
        litrosCampoTexto = litrosCampo.aTextoConEscala(ESCALA_LITROS),
        estadoTexto = estado.name,
    )
}
