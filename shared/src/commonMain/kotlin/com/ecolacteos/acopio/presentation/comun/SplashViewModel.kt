package com.ecolacteos.acopio.presentation.comun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.domain.usecase.DecidirDestinoInicialUseCase
import com.ecolacteos.acopio.domain.usecase.DestinoInicial
import com.ecolacteos.acopio.domain.usecase.RefrescarSesionUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** `S-01 · Splash` (`MOBILE_SCREENS.md §4`). Solo un estado: cargando (máx. ~800ms antes del indicador). */
data class SplashUiState(val cargando: Boolean = true)

enum class DestinoSplash { HOME, LOGIN, LOGIN_SESION_VENCIDA }

sealed interface SplashEffect {
    data class Navegar(val destino: DestinoSplash) : SplashEffect
}

/**
 * `§4`: "el splash nunca bloquea esperando red". La decisión de destino ([DecidirDestinoInicialUseCase])
 * es puramente local; el refresh proactivo ([RefrescarSesionUseCase]) se dispara aparte, sin `await`, para
 * que nunca retrase la navegación (trampa #11 de `PROMPT_FASE_07.md`).
 */
class SplashViewModel(
    private val decidirDestinoInicialUseCase: DecidirDestinoInicialUseCase,
    private val refrescarSesionUseCase: RefrescarSesionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    private val _effect = Channel<SplashEffect>(Channel.BUFFERED)
    val effect: Flow<SplashEffect> = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            val destino = decidirDestinoInicialUseCase()
            if (destino is DestinoInicial.Home) {
                // Fire-and-forget a propósito -- no se espera antes de navegar (§4, trampa #11).
                viewModelScope.launch { refrescarSesionUseCase() }
            }
            _uiState.value = SplashUiState(cargando = false)
            _effect.send(SplashEffect.Navegar(destino.aDestinoSplash()))
        }
    }

    private fun DestinoInicial.aDestinoSplash(): DestinoSplash = when (this) {
        DestinoInicial.Home -> DestinoSplash.HOME
        DestinoInicial.Login -> DestinoSplash.LOGIN
        DestinoInicial.LoginSesionVencida -> DestinoSplash.LOGIN_SESION_VENCIDA
    }
}
