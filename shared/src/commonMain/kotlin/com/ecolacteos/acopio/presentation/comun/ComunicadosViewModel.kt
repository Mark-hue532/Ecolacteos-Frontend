package com.ecolacteos.acopio.presentation.comun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.domain.model.Comunicado
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.presentation.formateada
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ItemComunicadoUiState(
    val id: String,
    val mensaje: String,
    /** `fecha` es `LocalDateTime`, no `LocalDate` (`MOBILE_DATA_MAPPING.md §5.6`, trampa #10). */
    val fechaTexto: String,
    val zonasTexto: String,
    val confirmado: Boolean,
)

data class ComunicadosUiState(
    val items: List<ItemComunicadoUiState> = emptyList(),
    val cargando: Boolean = true,
    val hayConexion: Boolean = true,
) {
    val vacio: Boolean get() = !cargando && items.isEmpty()
}

sealed interface ComunicadosEvent {
    data class ConfirmarPresionado(val comunicadoId: String) : ComunicadosEvent
}

sealed interface ComunicadosEffect {
    data class NavegarAConfirmar(val comunicadoId: String) : ComunicadosEffect
}

/**
 * `S-06 · Comunicados` (`MOBILE_SCREENS.md §4`, READ-CACHE). Lee `comunicado_cache` vía
 * `ObservarCatalogosUseCase` (ya existe desde Fase 6 -- decisión del checkpoint: no duplicar un `UseCase`
 * nuevo para lo mismo). **Nunca** llama a `GET /api/comunicados/zona/{zonaId}` directo: ese endpoint exige
 * `zonaId`, que es exactamente `DATA-016` (`CLAUDE.md §7`) -- leer del cache evita el problema por completo,
 * mismo criterio que `V-01` en la Fase 7.
 */
class ComunicadosViewModel(
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
    private val confirmacionesRecientesEnMemoria: ConfirmacionesRecientesEnMemoria,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComunicadosUiState())
    val uiState: StateFlow<ComunicadosUiState> = _uiState.asStateFlow()

    private val _effect = Channel<ComunicadosEffect>(Channel.BUFFERED)
    val effect: Flow<ComunicadosEffect> = _effect.receiveAsFlow()

    init {
        combine(
            observarCatalogosUseCase.comunicados(),
            observarConectividadUseCase(),
            confirmacionesRecientesEnMemoria.confirmados,
        ) { comunicados, conectado, confirmados ->
            ComunicadosUiState(
                items = comunicados.sortedByDescending { it.fecha }.map { it.aItemUi(confirmados) },
                cargando = false,
                hayConexion = conectado,
            )
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    fun onEvent(evento: ComunicadosEvent) {
        when (evento) {
            is ComunicadosEvent.ConfirmarPresionado ->
                viewModelScope.launch { _effect.send(ComunicadosEffect.NavegarAConfirmar(evento.comunicadoId)) }
        }
    }

    private fun Comunicado.aItemUi(confirmados: Set<String>): ItemComunicadoUiState = ItemComunicadoUiState(
        id = id,
        mensaje = mensaje,
        fechaTexto = fecha.formateada(),
        zonasTexto = zonasNombres.joinToString(", "),
        confirmado = id in confirmados,
    )
}
