package com.ecolacteos.acopio.presentation.acopio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.usecase.BuscarProveedorPorNombreUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BuscarProveedorUiState(
    val query: String = "",
    val resultados: List<Proveedor> = emptyList(),
    /** `false` -> el catálogo entero está vacío: el vacío es "sin catálogo", no "ningún proveedor coincide". */
    val catalogoDescargado: Boolean = true,
    val cargando: Boolean = true,
) {
    val vacioSinCoincidencias: Boolean get() = !cargando && catalogoDescargado && resultados.isEmpty()
    val vacioSinCatalogo: Boolean get() = !cargando && !catalogoDescargado
}

sealed interface BuscarProveedorEvent {
    data class QueryCambio(val texto: String) : BuscarProveedorEvent
    data class ProveedorSeleccionado(val proveedorId: String) : BuscarProveedorEvent
}

sealed interface BuscarProveedorEffect {
    data class NavegarARegistrar(val proveedorId: String) : BuscarProveedorEffect
}

/** `A-03 · Buscar proveedor` (`MOBILE_SCREENS.md §5`, OFFLINE REAL) -- filtro reactivo sobre `proveedor_cache`. */
@OptIn(ExperimentalCoroutinesApi::class)
class BuscarProveedorViewModel(
    private val buscarProveedorPorNombreUseCase: BuscarProveedorPorNombreUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _uiState = MutableStateFlow(BuscarProveedorUiState())
    val uiState: StateFlow<BuscarProveedorUiState> = _uiState.asStateFlow()

    private val _effect = Channel<BuscarProveedorEffect>(Channel.BUFFERED)
    val effect: Flow<BuscarProveedorEffect> = _effect.receiveAsFlow()

    init {
        query.flatMapLatest { texto -> buscarProveedorPorNombreUseCase(texto) }
            .combine(observarCatalogosUseCase.proveedores()) { resultados, todos -> resultados to todos }
            .onEach { (resultados, todos) ->
                _uiState.update { it.copy(cargando = false, resultados = resultados, catalogoDescargado = todos.isNotEmpty()) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(evento: BuscarProveedorEvent) {
        when (evento) {
            is BuscarProveedorEvent.QueryCambio -> {
                _uiState.update { it.copy(query = evento.texto) }
                query.value = evento.texto
            }
            is BuscarProveedorEvent.ProveedorSeleccionado ->
                viewModelScope.launch { _effect.send(BuscarProveedorEffect.NavegarARegistrar(evento.proveedorId)) }
        }
    }
}
