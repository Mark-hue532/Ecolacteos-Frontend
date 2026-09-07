package com.ecolacteos.acopio.presentation.comun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.VERSION_APP
import com.ecolacteos.acopio.data.remote.dto.Rol
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.usecase.LogoutUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarEstadoSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarPendientesUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarResumenSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ResultadoLogout
import com.ecolacteos.acopio.domain.usecase.SincronizarAhoraUseCase
import com.ecolacteos.acopio.presentation.formateada
import com.ecolacteos.acopio.synchronization.EstadoSync
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AjustesUiState(
    val nombre: String = "",
    val rol: Rol = Rol.UNKNOWN,
    val versionApp: String = VERSION_APP,
    val ultimoSyncTexto: String? = null,
    val hayConexion: Boolean = true,
    val pendientesConteo: Int = 0,
    val sincronizando: Boolean = false,
    val cerrandoSesion: Boolean = false,
    /** "Tenés N registros sin enviar" con las 4 opciones del árbol de `§4` (`MOBILE_ARCHITECTURE.md §4`). */
    val mostrarDialogoBloqueado: Boolean = false,
    /** Segunda confirmación explícita antes de "cerrar sesión igual" -- nombra qué se conserva y qué se borra. */
    val mostrarConfirmacionCerrarIgual: Boolean = false,
)

sealed interface AjustesEvent {
    data object CerrarSesionPresionado : AjustesEvent
    data object SincronizarAhoraPresionado : AjustesEvent
    data object VerPendientesPresionado : AjustesEvent
    data object CerrarSesionIgualPresionado : AjustesEvent
    data object ConfirmarCerrarIgualPresionado : AjustesEvent
    data object CancelarDialogoPresionado : AjustesEvent
}

sealed interface AjustesEffect {
    data object NavegarALogin : AjustesEffect
    data object NavegarAPendientes : AjustesEffect
}

private data class DatosReactivos(
    val totalPendientes: Int,
    val hayConexion: Boolean,
    val ultimoSyncTexto: String?,
    val sincronizando: Boolean,
)

/**
 * `S-07 · Ajustes y sesión` (`MOBILE_SCREENS.md §4`) -- el punto delicado. Implementa el árbol de decisión
 * de `MOBILE_ARCHITECTURE.md §4` literal, sobre `LogoutUseCase` (Fase 6), que ya lo tiene todo -- esta
 * pantalla es la primera que lo expone. "Al terminar, reevaluar" (`§4`) se resuelve solo: mientras el
 * diálogo de bloqueo está abierto, sigue observando `ObservarPendientesUseCase()` reactivo; si el conteo
 * llega a 0 (por "Sincronizar ahora" u otro ciclo oportunista), completa el logout normal sin que el
 * usuario tenga que volver a tocar nada.
 */
class AjustesViewModel(
    private val gestorSesion: GestorSesion,
    private val logoutUseCase: LogoutUseCase,
    private val observarPendientesUseCase: ObservarPendientesUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
    private val observarResumenSyncUseCase: ObservarResumenSyncUseCase,
    private val observarEstadoSyncUseCase: ObservarEstadoSyncUseCase,
    private val sincronizarAhoraUseCase: SincronizarAhoraUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AjustesUiState())
    val uiState: StateFlow<AjustesUiState> = _uiState.asStateFlow()

    private val _effect = Channel<AjustesEffect>(Channel.BUFFERED)
    val effect: Flow<AjustesEffect> = _effect.receiveAsFlow()

    init {
        val sesion = gestorSesion.sesion.value
        _uiState.update { it.copy(nombre = sesion?.nombre.orEmpty(), rol = sesion?.rol ?: Rol.UNKNOWN) }

        combine(
            observarPendientesUseCase(),
            observarConectividadUseCase(),
            observarResumenSyncUseCase(),
            observarEstadoSyncUseCase(),
        ) { resumen, conectado, resumenSync, estadoMotor ->
            DatosReactivos(resumen.total, conectado, resumenSync.ultimoSyncOk?.formateada(), estadoMotor == EstadoSync.SINCRONIZANDO)
        }.onEach { datos ->
            _uiState.update {
                it.copy(
                    pendientesConteo = datos.totalPendientes,
                    hayConexion = datos.hayConexion,
                    ultimoSyncTexto = datos.ultimoSyncTexto,
                    sincronizando = datos.sincronizando,
                )
            }
            if (_uiState.value.mostrarDialogoBloqueado && datos.totalPendientes == 0) {
                completarLogout(conservarDatos = false)
            }
        }.launchIn(viewModelScope)
    }

    fun onEvent(evento: AjustesEvent) {
        when (evento) {
            AjustesEvent.CerrarSesionPresionado -> intentarLogout()
            AjustesEvent.SincronizarAhoraPresionado -> viewModelScope.launch { sincronizarAhoraUseCase() }
            AjustesEvent.VerPendientesPresionado -> viewModelScope.launch { _effect.send(AjustesEffect.NavegarAPendientes) }
            AjustesEvent.CerrarSesionIgualPresionado -> _uiState.update { it.copy(mostrarConfirmacionCerrarIgual = true) }
            AjustesEvent.ConfirmarCerrarIgualPresionado -> completarLogout(conservarDatos = true)
            AjustesEvent.CancelarDialogoPresionado ->
                _uiState.update { it.copy(mostrarDialogoBloqueado = false, mostrarConfirmacionCerrarIgual = false) }
        }
    }

    private fun intentarLogout() {
        _uiState.update { it.copy(cerrandoSesion = true) }
        viewModelScope.launch {
            when (logoutUseCase(conservarDatos = false)) {
                ResultadoLogout.Cerrada -> {
                    _uiState.update { it.copy(cerrandoSesion = false) }
                    _effect.send(AjustesEffect.NavegarALogin)
                }
                is ResultadoLogout.BloqueadaPorPendientes ->
                    _uiState.update { it.copy(cerrandoSesion = false, mostrarDialogoBloqueado = true) }
            }
        }
    }

    /**
     * Camino único para "cerrar sesión igual" (`conservarDatos = true`) y para el cierre normal una vez
     * que la reevaluación reactiva confirma 0 pendientes (`conservarDatos = false`, ya sabido `Cerrada`).
     */
    private fun completarLogout(conservarDatos: Boolean) {
        _uiState.update { it.copy(cerrandoSesion = true) }
        viewModelScope.launch {
            logoutUseCase(conservarDatos = conservarDatos)
            _uiState.update { it.copy(cerrandoSesion = false, mostrarDialogoBloqueado = false, mostrarConfirmacionCerrarIgual = false) }
            _effect.send(AjustesEffect.NavegarALogin)
        }
    }
}
