package com.ecolacteos.acopio.presentation.comun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.usecase.ConEstado
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarEstadoSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarPendientesUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarResumenSyncUseCase
import com.ecolacteos.acopio.domain.usecase.SincronizarAhoraUseCase
import com.ecolacteos.acopio.presentation.formateada
import com.ecolacteos.acopio.synchronization.EstadoSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Desglose de un recurso (`MOBILE_SCREENS.md §4`, `S-04`): "cuántos PENDING, PENDING_DEPENDENCY, SYNCING y FAILED". */
data class RecursoSyncUiState(
    val pendientes: Int = 0,
    val enEspera: Int = 0,
    val sincronizando: Int = 0,
    val conError: Int = 0,
) {
    val total: Int get() = pendientes + enEspera + sincronizando + conError
}

data class EstadoSincronizacionUiState(
    val registroAcopio: RecursoSyncUiState = RecursoSyncUiState(),
    val analisisCalidad: RecursoSyncUiState = RecursoSyncUiState(),
    val loteProduccion: RecursoSyncUiState = RecursoSyncUiState(),
    val venta: RecursoSyncUiState = RecursoSyncUiState(),
    val hayConexion: Boolean = true,
    val sincronizandoAhora: Boolean = false,
    val ultimoSyncTexto: String? = null,
) {
    val totalPendiente: Int get() = registroAcopio.total + analisisCalidad.total + loteProduccion.total + venta.total

    /** "Todo al día" (`§4`) -- estado vacío **positivo**, no un error. */
    val todoAlDia: Boolean get() = totalPendiente == 0
}

sealed interface EstadoSincronizacionEvent {
    data object SincronizarAhoraPresionado : EstadoSincronizacionEvent
}

/**
 * `S-04` (`MOBILE_SCREENS.md §4`). `PENDING_DEPENDENCY` nunca se pinta como error (`§10.5`, trampa #9) --
 * por eso [RecursoSyncUiState.enEspera] es un campo separado de [RecursoSyncUiState.conError], nunca sumado.
 */
class EstadoSincronizacionViewModel(
    observarPendientesUseCase: ObservarPendientesUseCase,
    observarResumenSyncUseCase: ObservarResumenSyncUseCase,
    observarConectividadUseCase: ObservarConectividadUseCase,
    observarEstadoSyncUseCase: ObservarEstadoSyncUseCase,
    private val sincronizarAhoraUseCase: SincronizarAhoraUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EstadoSincronizacionUiState())
    val uiState: StateFlow<EstadoSincronizacionUiState> = _uiState.asStateFlow()

    init {
        combine(
            observarPendientesUseCase(),
            observarResumenSyncUseCase(),
            observarConectividadUseCase(),
            observarEstadoSyncUseCase(),
        ) { resumen, resumenSync, conectado, estadoMotor ->
            EstadoSincronizacionUiState(
                registroAcopio = resumen.registros.aRecursoUi(),
                analisisCalidad = resumen.analisis.aRecursoUi(),
                loteProduccion = resumen.lotes.aRecursoUi(),
                venta = resumen.ventas.aRecursoUi(),
                hayConexion = conectado,
                sincronizandoAhora = estadoMotor == EstadoSync.SINCRONIZANDO,
                ultimoSyncTexto = resumenSync.ultimoSyncOk?.formateada(),
            )
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    fun onEvent(evento: EstadoSincronizacionEvent) {
        when (evento) {
            EstadoSincronizacionEvent.SincronizarAhoraPresionado -> viewModelScope.launch { sincronizarAhoraUseCase() }
        }
    }

    private fun List<ConEstado<*>>.aRecursoUi(): RecursoSyncUiState = RecursoSyncUiState(
        pendientes = count { it.estado is EstadoSincronizacion.Pendiente },
        enEspera = count { it.estado is EstadoSincronizacion.EsperandoDependencia },
        sincronizando = count { it.estado is EstadoSincronizacion.Sincronizando },
        conError = count { it.estado is EstadoSincronizacion.Fallido },
    )
}
