package com.ecolacteos.acopio.presentation.acopio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.data.repository.ItemHistorialRegistroAcopio
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarHistorialProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRegistrosDeProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.estadoSincronizacionDe
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
import kotlinx.datetime.LocalDateTime

private const val ESCALA_LITROS = 2

/**
 * Una fila de `A-05`. [id] es el `server_id` -- `null` si es un registro **propio** que todavía no
 * sincronizó (nada que ver en `A-06` todavía). [estadoSync] es `null` para un registro **ajeno**: no tiene
 * ciclo de sync propio en este dispositivo.
 */
data class ItemHistorialUiState(
    val id: String?,
    val fechaHoraTexto: String,
    val litrosTexto: String,
    val tieneObservacion: Boolean,
    val estadoSync: EstadoSincronizacion?,
)

data class HistorialProveedorUiState(
    val proveedorId: String,
    val items: List<ItemHistorialUiState> = emptyList(),
    val cargando: Boolean = true,
    val hayConexion: Boolean = true,
) {
    val vacio: Boolean get() = !cargando && items.isEmpty()
}

sealed interface HistorialProveedorEvent {
    data class ItemSeleccionado(val id: String) : HistorialProveedorEvent
}

sealed interface HistorialProveedorEffect {
    data class NavegarADetalle(val id: String) : HistorialProveedorEffect
}

/**
 * `A-05 · Historial de entregas del proveedor` (`MOBILE_SCREENS.md §5`, ONLINE+CACHE). Decisión #4 del
 * checkpoint: [obtenerRegistrosDeProveedorUseCase] dispara el refresco de `registro_acopio_cache` **una
 * sola vez**, en `init` -- nunca en cada recomposición (trampa #2 de `PROMPT_FASE_06.md §10`); la lista que
 * ve la pantalla la arma [observarHistorialProveedorUseCase], 100% reactivo sobre SQLite (propios + ajenos
 * ya combinados y deduplicados por `DATA-013`, ver `RegistroAcopioRepository`).
 */
class HistorialProveedorViewModel(
    proveedorId: String,
    private val observarHistorialProveedorUseCase: ObservarHistorialProveedorUseCase,
    private val obtenerRegistrosDeProveedorUseCase: ObtenerRegistrosDeProveedorUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistorialProveedorUiState(proveedorId = proveedorId))
    val uiState: StateFlow<HistorialProveedorUiState> = _uiState.asStateFlow()

    private val _effect = Channel<HistorialProveedorEffect>(Channel.BUFFERED)
    val effect: Flow<HistorialProveedorEffect> = _effect.receiveAsFlow()

    init {
        observarHistorialProveedorUseCase(proveedorId)
            .onEach { items ->
                _uiState.update {
                    it.copy(cargando = false, items = items.sortedByDescending { item -> item.fechaHoraDe() }.map { item -> item.aItemUi() })
                }
            }
            .launchIn(viewModelScope)

        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { obtenerRegistrosDeProveedorUseCase(proveedorId) }
    }

    fun onEvent(evento: HistorialProveedorEvent) {
        when (evento) {
            is HistorialProveedorEvent.ItemSeleccionado ->
                viewModelScope.launch { _effect.send(HistorialProveedorEffect.NavegarADetalle(evento.id)) }
        }
    }

    private fun ItemHistorialRegistroAcopio.fechaHoraDe(): LocalDateTime = when (this) {
        is ItemHistorialRegistroAcopio.Propio -> registro.fechaHora
        is ItemHistorialRegistroAcopio.Ajeno -> referencia.fechaHora
    }

    private fun ItemHistorialRegistroAcopio.aItemUi(): ItemHistorialUiState = when (this) {
        is ItemHistorialRegistroAcopio.Propio -> ItemHistorialUiState(
            id = registro.serverId,
            fechaHoraTexto = registro.fechaHora.formateada(),
            litrosTexto = registro.litros.aTextoConEscala(ESCALA_LITROS),
            tieneObservacion = registro.motivoObservacionId != null,
            estadoSync = estadoSincronizacionDe(registro.syncStatus, registro.syncError, registro.nextAttemptAt),
        )
        is ItemHistorialRegistroAcopio.Ajeno -> ItemHistorialUiState(
            id = referencia.id,
            fechaHoraTexto = referencia.fechaHora.formateada(),
            litrosTexto = referencia.litros.aTextoConEscala(ESCALA_LITROS),
            tieneObservacion = referencia.tieneObservacion ?: false,
            estadoSync = null,
        )
    }
}
