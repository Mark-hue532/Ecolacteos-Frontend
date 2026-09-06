package com.ecolacteos.acopio.presentation.comun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.ApiError
import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.domain.usecase.LoginUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.presentation.aMensajeUi
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

private val REGEX_EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** `S-02 · Login` (`MOBILE_SCREENS.md §4`) -- `UiState` literal del documento. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val verPassword: Boolean = false,
    val enviando: Boolean = false,
    val errorEmail: String? = null,
    val errorPassword: String? = null,
    val errorGeneral: String? = null,
    val hayConexion: Boolean = true,
    val puedeEnviar: Boolean = false,
)

sealed interface LoginEvent {
    data class EmailCambio(val valor: String) : LoginEvent
    data class PasswordCambio(val valor: String) : LoginEvent
    data object AlternarVerPassword : LoginEvent
    data object EnviarPresionado : LoginEvent
}

sealed interface LoginEffect {
    data object NavegarAHome : LoginEffect
}

/**
 * `ONLINE-ONLY` (`§4`): sin conexión el botón se deshabilita, nunca se intenta la llamada (trampa #11 --
 * acá el equivalente es no gastar 30s de timeout). Un 401 **nunca** distingue email de password (trampa
 * #10) -- se intercepta antes de pasar por [aMensajeUi] genérico, que en un 401 normal cerraría sesión.
 */
class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _effect = Channel<LoginEffect>(Channel.BUFFERED)
    val effect: Flow<LoginEffect> = _effect.receiveAsFlow()

    init {
        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado).recalcularPuedeEnviar() } }
            .launchIn(viewModelScope)
    }

    fun onEvent(evento: LoginEvent) {
        when (evento) {
            is LoginEvent.EmailCambio -> _uiState.update {
                it.copy(email = evento.valor, errorEmail = null, errorGeneral = null).recalcularPuedeEnviar()
            }
            is LoginEvent.PasswordCambio -> _uiState.update {
                it.copy(password = evento.valor, errorPassword = null, errorGeneral = null).recalcularPuedeEnviar()
            }
            LoginEvent.AlternarVerPassword -> _uiState.update { it.copy(verPassword = !it.verPassword) }
            LoginEvent.EnviarPresionado -> enviar()
        }
    }

    private fun enviar() {
        val estado = _uiState.value
        if (!estado.puedeEnviar) return

        val errorEmail = if (!REGEX_EMAIL.matches(estado.email)) "Ingresá un correo válido" else null
        val errorPassword = if (estado.password.isBlank()) "Ingresá tu contraseña" else null
        if (errorEmail != null || errorPassword != null) {
            _uiState.update { it.copy(errorEmail = errorEmail, errorPassword = errorPassword) }
            return
        }

        _uiState.update { it.copy(enviando = true, errorGeneral = null).recalcularPuedeEnviar() }
        viewModelScope.launch {
            when (val resultado = loginUseCase(estado.email, estado.password)) {
                is ApiResult.Exito -> _effect.send(LoginEffect.NavegarAHome)
                is ApiResult.Error -> _uiState.update {
                    it.copy(enviando = false, errorGeneral = resultado.error.aMensajeLogin()).recalcularPuedeEnviar()
                }
            }
        }
    }

    /** `§4`: "nunca se distingue cuál de los dos falló" -- caso especial que [aMensajeUi] no cubre solo. */
    private fun ApiError.aMensajeLogin(): String =
        if (this is ApiError.NoAutorizado) "Correo o contraseña incorrectos" else aMensajeUi().texto

    private fun LoginUiState.recalcularPuedeEnviar(): LoginUiState =
        copy(puedeEnviar = hayConexion && !enviando && email.isNotBlank() && password.isNotBlank())
}
