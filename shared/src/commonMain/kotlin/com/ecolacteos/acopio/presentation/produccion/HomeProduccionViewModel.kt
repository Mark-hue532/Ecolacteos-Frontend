package com.ecolacteos.acopio.presentation.produccion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.usecase.LoteConTipoQueso
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarLotesRecientesUseCase
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

private const val ESCALA_LITROS = 2
private const val ESCALA_RENDIMIENTO = 2

/**
 * Una fila de `P-01`. [rendimientoEsperadoTexto] es siempre el **esperado** del tipo de queso -- nunca un
 * rendimiento real "previsto" (decisión 2 del checkpoint): `P-01` es offline OK
 * (`MOBILE_SCREENS.md §7`) y el rendimiento real solo lo confirma el servidor (`P-04`).
 */
data class ItemLoteUiState(
    val uuidCliente: String,
    val serverId: String?,
    val fechaTexto: String,
    val tipoQuesoNombreTexto: String,
    val litrosUsadosTexto: String,
    val unidadesObtenidas: Int,
    val rendimientoEsperadoTexto: String,
    val estadoSync: EstadoSincronizacion,
)

data class HomeProduccionUiState(
    val items: List<ItemLoteUiState> = emptyList(),
    val cargando: Boolean = true,
    val hayConexion: Boolean = true,
) {
    val vacio: Boolean get() = !cargando && items.isEmpty()
}

sealed interface HomeProduccionEvent {
    /** Solo navega si la fila ya tiene `serverId` (`P-04` necesita el id de servidor, no el `uuidCliente`). */
    data class LoteSeleccionado(val serverId: String) : HomeProduccionEvent
    data object RegistrarLotePresionado : HomeProduccionEvent
}

sealed interface HomeProduccionEffect {
    data class NavegarADetalleLote(val serverId: String) : HomeProduccionEffect
    data object NavegarABuscarProveedor : HomeProduccionEffect
}

/** `P-01 · Home producción` (Fase 8D, `MOBILE_SCREENS.md §7`, offline OK). */
class HomeProduccionViewModel(
    private val observarLotesRecientesUseCase: ObservarLotesRecientesUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeProduccionUiState())
    val uiState: StateFlow<HomeProduccionUiState> = _uiState.asStateFlow()

    private val _effect = Channel<HomeProduccionEffect>(Channel.BUFFERED)
    val effect: Flow<HomeProduccionEffect> = _effect.receiveAsFlow()

    init {
        observarLotesRecientesUseCase()
            .onEach { lotes ->
                val ordenados = lotes.sortedByDescending { it.lote.fecha }
                _uiState.update { it.copy(cargando = false, items = ordenados.map { item -> item.aItemUi() }) }
            }
            .launchIn(viewModelScope)

        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(evento: HomeProduccionEvent) {
        when (evento) {
            is HomeProduccionEvent.LoteSeleccionado ->
                viewModelScope.launch { _effect.send(HomeProduccionEffect.NavegarADetalleLote(evento.serverId)) }
            HomeProduccionEvent.RegistrarLotePresionado ->
                viewModelScope.launch { _effect.send(HomeProduccionEffect.NavegarABuscarProveedor) }
        }
    }

    private fun LoteConTipoQueso.aItemUi(): ItemLoteUiState = ItemLoteUiState(
        uuidCliente = lote.uuidCliente,
        serverId = lote.serverId,
        fechaTexto = lote.fecha.formateada(),
        tipoQuesoNombreTexto = tipoQuesoNombre,
        litrosUsadosTexto = lote.litrosUsados.aTextoConEscala(ESCALA_LITROS),
        unidadesObtenidas = lote.unidadesObtenidas,
        rendimientoEsperadoTexto = rendimientoEsperadoPct.aTextoConEscala(ESCALA_RENDIMIENTO),
        estadoSync = estadoSincronizacionDe(lote.syncStatus, lote.syncError, lote.nextAttemptAt),
    )
}
