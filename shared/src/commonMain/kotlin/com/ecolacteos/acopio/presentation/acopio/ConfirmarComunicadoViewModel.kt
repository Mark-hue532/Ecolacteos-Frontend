package com.ecolacteos.acopio.presentation.acopio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.usecase.BuscarProveedorPorNombreUseCase
import com.ecolacteos.acopio.domain.usecase.ConfirmarComunicadoUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.presentation.comun.ConfirmacionesRecientesEnMemoria
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConfirmarComunicadoUiState(
    val mensajeComunicado: String? = null,
    val query: String = "",
    val resultados: List<Proveedor> = emptyList(),
    val proveedorSeleccionado: Proveedor? = null,
    val hayConexion: Boolean = true,
    val confirmando: Boolean = false,
    val error: String? = null,
) {
    val puedeConfirmar: Boolean get() = hayConexion && !confirmando && proveedorSeleccionado != null
}

sealed interface ConfirmarComunicadoEvent {
    data class QueryCambio(val texto: String) : ConfirmarComunicadoEvent
    data class ProveedorSeleccionado(val proveedor: Proveedor) : ConfirmarComunicadoEvent
    data object ConfirmarPresionado : ConfirmarComunicadoEvent
}

sealed interface ConfirmarComunicadoEffect {
    data object ConfirmadoConExito : ConfirmarComunicadoEffect
}

/**
 * `A-07 · Confirmar comunicado a proveedor` (`MOBILE_SCREENS.md §5`, ONLINE-ONLY). `DATA-005`/`§18.2`:
 * sin cola, sin reintento automático (trampa #9 de `PROMPT_FASE_08B.md`) -- un duplicado ensucia la
 * auditoría de "quién confirmó". Al confirmar, marca [ConfirmacionesRecientesEnMemoria] para que `S-06` lo
 * refleje -- ver esa clase para por qué es deliberadamente efímero.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmarComunicadoViewModel(
    private val comunicadoId: String,
    private val confirmarComunicadoUseCase: ConfirmarComunicadoUseCase,
    private val buscarProveedorPorNombreUseCase: BuscarProveedorPorNombreUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
    private val confirmacionesRecientesEnMemoria: ConfirmacionesRecientesEnMemoria,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _uiState = MutableStateFlow(ConfirmarComunicadoUiState())
    val uiState: StateFlow<ConfirmarComunicadoUiState> = _uiState.asStateFlow()

    private val _effect = Channel<ConfirmarComunicadoEffect>(Channel.BUFFERED)
    val effect: Flow<ConfirmarComunicadoEffect> = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            val mensaje = observarCatalogosUseCase.comunicados().first().firstOrNull { it.id == comunicadoId }?.mensaje
            _uiState.update { it.copy(mensajeComunicado = mensaje) }
        }

        query.flatMapLatest { texto -> buscarProveedorPorNombreUseCase(texto) }
            .combine(observarConectividadUseCase()) { resultados, conectado -> resultados to conectado }
            .onEach { (resultados, conectado) -> _uiState.update { it.copy(resultados = resultados, hayConexion = conectado) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(evento: ConfirmarComunicadoEvent) {
        when (evento) {
            is ConfirmarComunicadoEvent.QueryCambio -> {
                _uiState.update { it.copy(query = evento.texto, proveedorSeleccionado = null) }
                query.value = evento.texto
            }
            is ConfirmarComunicadoEvent.ProveedorSeleccionado ->
                _uiState.update { it.copy(proveedorSeleccionado = evento.proveedor) }
            ConfirmarComunicadoEvent.ConfirmarPresionado -> confirmar()
        }
    }

    private fun confirmar() {
        // Defensa en profundidad, no solo el botón deshabilitado de la UI (trampa #9 -- A-07 no se encola
        // ni se dispara sin conexión, y esta es la única entrada real al `UseCase` online-only).
        if (!_uiState.value.puedeConfirmar) return
        val proveedor = _uiState.value.proveedorSeleccionado ?: return
        _uiState.update { it.copy(confirmando = true, error = null) }
        viewModelScope.launch {
            when (val resultado = confirmarComunicadoUseCase(comunicadoId, proveedor.id)) {
                is ResultadoDominio.Exito -> {
                    confirmacionesRecientesEnMemoria.marcarConfirmado(comunicadoId)
                    _uiState.update { it.copy(confirmando = false) }
                    _effect.send(ConfirmarComunicadoEffect.ConfirmadoConExito)
                }
                // El mensaje del backend se muestra literal (§10.4) -- nunca reinterpretado.
                is ResultadoDominio.Error -> _uiState.update { it.copy(confirmando = false, error = resultado.error.mensaje) }
            }
        }
    }
}
