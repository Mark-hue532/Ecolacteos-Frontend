package com.ecolacteos.acopio.presentation.calidad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.core.decimalDesdeTexto
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.usecase.AnexarCorreccionUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleRegistroAcopioUseCase
import kotlinx.coroutines.CancellationException
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

/**
 * `RegistrarCorreccionUiState` (`C-06`, `MOBILE_SCREENS.md §6`). [litrosActualTexto] sale de
 * `ObtenerDetalleRegistroAcopioUseCase` (el mismo que usa `A-06`), resuelto **antes** de mostrar la
 * confirmación -- nunca de la respuesta del `POST`, que todavía no existe en ese momento (trampa: el
 * `litrosAnterior` del diálogo tiene que salir de datos que ya tenemos, `§6`).
 */
data class RegistrarCorreccionUiState(
    val cargandoActual: Boolean = true,
    val litrosActualTexto: String? = null,
    val litrosCorregidoTexto: String = "",
    val motivoTexto: String = "",
    val errorLitrosCorregido: String? = null,
    val hayConexion: Boolean = true,
    val enviando: Boolean = false,
    val mostrarConfirmacion: Boolean = false,
    val mensajeError: String? = null,
) {
    val puedeEnviar: Boolean get() = !enviando && hayConexion && !cargandoActual && litrosCorregidoTexto.isNotBlank() && errorLitrosCorregido == null
}

sealed interface RegistrarCorreccionEvent {
    data class LitrosCorregidoCambio(val texto: String) : RegistrarCorreccionEvent
    data class MotivoCambio(val texto: String) : RegistrarCorreccionEvent
    /** Abre la confirmación -- **todavía no envía nada** (`§6`: confirmación obligatoria). */
    data object EnviarPresionado : RegistrarCorreccionEvent
    data object ConfirmarPresionado : RegistrarCorreccionEvent
    data object CancelarConfirmacionPresionado : RegistrarCorreccionEvent
}

sealed interface RegistrarCorreccionEffect {
    data object GuardadoConExito : RegistrarCorreccionEffect
}

/**
 * `C-06 · Registrar corrección de litros` (Fase 8E, `MOBILE_SCREENS.md §6`) -- ONLINE-ONLY, sin cola
 * (`DATA-004`/`§18.7`, trampa #1): el endpoint no es idempotente, un reintento crea una corrección
 * duplicada que corrompe la trazabilidad de litros. Punto de entrada compartido por `A-06` y `C-04`
 * (`§0` del prompt).
 */
class RegistrarCorreccionViewModel(
    private val registroAcopioId: String,
    private val anexarCorreccionUseCase: AnexarCorreccionUseCase,
    private val obtenerDetalleRegistroAcopioUseCase: ObtenerDetalleRegistroAcopioUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrarCorreccionUiState())
    val uiState: StateFlow<RegistrarCorreccionUiState> = _uiState.asStateFlow()

    private val _effect = Channel<RegistrarCorreccionEffect>(Channel.BUFFERED)
    val effect: Flow<RegistrarCorreccionEffect> = _effect.receiveAsFlow()

    init {
        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val detalle = obtenerDetalleRegistroAcopioUseCase(registroAcopioId)
            _uiState.update { it.copy(cargandoActual = false, litrosActualTexto = detalle?.litros?.aTextoConEscala(ESCALA_LITROS)) }
        }
    }

    fun onEvent(evento: RegistrarCorreccionEvent) {
        when (evento) {
            is RegistrarCorreccionEvent.LitrosCorregidoCambio ->
                _uiState.update { it.copy(litrosCorregidoTexto = evento.texto, errorLitrosCorregido = null) }
            is RegistrarCorreccionEvent.MotivoCambio -> _uiState.update { it.copy(motivoTexto = evento.texto) }
            RegistrarCorreccionEvent.EnviarPresionado -> validarYAbrirConfirmacion()
            RegistrarCorreccionEvent.ConfirmarPresionado -> confirmar()
            RegistrarCorreccionEvent.CancelarConfirmacionPresionado -> _uiState.update { it.copy(mostrarConfirmacion = false) }
        }
    }

    private fun validarYAbrirConfirmacion() {
        val estado = _uiState.value
        val litros = estado.litrosCorregidoTexto.aDecimalOrNull()
        val error = when {
            litros == null -> "Ingresá una cantidad válida"
            litros.isNegative -> "Los litros corregidos no pueden ser negativos"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(errorLitrosCorregido = error) }
            return
        }
        _uiState.update { it.copy(mostrarConfirmacion = true) }
    }

    private fun confirmar() {
        val estado = _uiState.value
        if (!estado.hayConexion) return // defensa en profundidad -- la pantalla ya bloquea sin conexión
        val litros = estado.litrosCorregidoTexto.aDecimalOrNull() ?: return

        _uiState.update { it.copy(enviando = true, mostrarConfirmacion = false, mensajeError = null) }
        viewModelScope.launch {
            when (val resultado = anexarCorreccionUseCase(registroAcopioId, litros, estado.motivoTexto.trim().ifBlank { null })) {
                is ResultadoDominio.Exito -> {
                    _uiState.update { it.copy(enviando = false) }
                    _effect.send(RegistrarCorreccionEffect.GuardadoConExito)
                }
                is ResultadoDominio.Error -> _uiState.update { it.copy(enviando = false, mensajeError = resultado.error.mensaje) }
            }
        }
    }
}

private fun String.aDecimalOrNull(): Decimal? = try {
    decimalDesdeTexto(this)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
