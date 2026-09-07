package com.ecolacteos.acopio.presentation.recepcion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.core.decimalDesdeTexto
import com.ecolacteos.acopio.data.repository.NuevaRecepcionPlanta
import com.ecolacteos.acopio.data.repository.ResultadoRegistrarRecepcion
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.RecepcionPlanta
import com.ecolacteos.acopio.domain.model.Unidad
import com.ecolacteos.acopio.domain.usecase.BuscarRecepcionesUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.RegistrarRecepcionUseCase
import com.ecolacteos.acopio.presentation.formateada
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

private const val MAX_DIGITOS_ENTEROS_LITROS = 7 // precision=9, scale=2 -> 9-2
private const val TURNO_POR_DEFECTO = "UNICO"

/**
 * `R-02b` (`MOBILE_SCREENS.md §9`): [recepcionExistente] `null` solo si la búsqueda posterior al 409
 * también falló (caso raro, igual se explica sin inventar datos) -- [turnoReal] es siempre el valor que
 * realmente colisionó, resuelto server-side (`"UNICO"` si el usuario dejó el campo vacío, trampa #3):
 * nombrarlo tal cual evita confundir al usuario que nunca escribió esa palabra.
 */
data class ConflictoRecepcion(val recepcionExistente: RecepcionPlanta?, val turnoReal: String)

data class RegistrarRecepcionUiState(
    val fecha: LocalDate? = null,
    val fechaTexto: String = "",
    val unidades: List<Unidad> = emptyList(),
    val unidadSeleccionada: Unidad? = null,
    val turnoTexto: String = "",
    val litrosCampoTexto: String = "",
    val litrosPlantaTexto: String = "",
    val errorFecha: String? = null,
    val errorUnidad: String? = null,
    val errorLitrosCampo: String? = null,
    val errorLitrosPlanta: String? = null,
    val enviando: Boolean = false,
    val hayConexion: Boolean = true,
    val conflicto: ConflictoRecepcion? = null,
    val mensajeError: String? = null,
) {
    val puedeGuardar: Boolean
        get() = !enviando && hayConexion && unidadSeleccionada != null && litrosCampoTexto.isNotBlank() && litrosPlantaTexto.isNotBlank() &&
            errorLitrosCampo == null && errorLitrosPlanta == null && errorFecha == null
}

sealed interface RegistrarRecepcionEvent {
    data class FechaCambio(val valor: LocalDate) : RegistrarRecepcionEvent
    data class UnidadCambio(val valor: Unidad) : RegistrarRecepcionEvent
    data class TurnoCambio(val texto: String) : RegistrarRecepcionEvent
    data class LitrosCampoCambio(val texto: String) : RegistrarRecepcionEvent
    data class LitrosPlantaCambio(val texto: String) : RegistrarRecepcionEvent
    data object GuardarPresionado : RegistrarRecepcionEvent
    data object VerRegistroExistentePresionado : RegistrarRecepcionEvent
    data object CambiarTurnoPresionado : RegistrarRecepcionEvent
    data object CancelarConflictoPresionado : RegistrarRecepcionEvent
}

sealed interface RegistrarRecepcionEffect {
    data class NavegarAResultado(val id: String) : RegistrarRecepcionEffect
}

/**
 * `R-01 · Registrar recepción en planta ★` (Fase 8E, `MOBILE_SCREENS.md §9`) -- ONLINE-ONLY, sin cola
 * (`DATA-006`/`§18.3`, trampa #1): el endpoint no es idempotente, un reintento tras un fallo real
 * duplicaría la recepción. El 409 (`R-02b`) es el único conflicto real del sistema (`§8`) -- se maneja acá
 * mismo, nunca reintentando el `POST` a ciegas (trampa #2).
 */
class RegistrarRecepcionViewModel(
    private val registrarRecepcionUseCase: RegistrarRecepcionUseCase,
    private val buscarRecepcionesUseCase: BuscarRecepcionesUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val hoy: LocalDate = ahoraComoFechaHora(reloj, zona).date

    private val _uiState = MutableStateFlow(RegistrarRecepcionUiState(fecha = hoy, fechaTexto = hoy.formateada()))
    val uiState: StateFlow<RegistrarRecepcionUiState> = _uiState.asStateFlow()

    private val _effect = Channel<RegistrarRecepcionEffect>(Channel.BUFFERED)
    val effect: Flow<RegistrarRecepcionEffect> = _effect.receiveAsFlow()

    init {
        observarCatalogosUseCase.unidades()
            .onEach { unidades -> _uiState.update { it.copy(unidades = unidades) } }
            .launchIn(viewModelScope)

        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(evento: RegistrarRecepcionEvent) {
        when (evento) {
            is RegistrarRecepcionEvent.FechaCambio -> _uiState.update { it.copy(fecha = evento.valor, fechaTexto = evento.valor.formateada(), errorFecha = null) }
            is RegistrarRecepcionEvent.UnidadCambio -> _uiState.update { it.copy(unidadSeleccionada = evento.valor, errorUnidad = null) }
            is RegistrarRecepcionEvent.TurnoCambio -> _uiState.update { it.copy(turnoTexto = evento.texto) }
            is RegistrarRecepcionEvent.LitrosCampoCambio -> _uiState.update { it.copy(litrosCampoTexto = evento.texto, errorLitrosCampo = null) }
            is RegistrarRecepcionEvent.LitrosPlantaCambio -> _uiState.update { it.copy(litrosPlantaTexto = evento.texto, errorLitrosPlanta = null) }
            RegistrarRecepcionEvent.GuardarPresionado -> guardar()
            RegistrarRecepcionEvent.VerRegistroExistentePresionado -> verRegistroExistente()
            RegistrarRecepcionEvent.CambiarTurnoPresionado -> _uiState.update { it.copy(conflicto = null) }
            RegistrarRecepcionEvent.CancelarConflictoPresionado -> _uiState.update { it.copy(conflicto = null) }
        }
    }

    private fun verRegistroExistente() {
        val existente = _uiState.value.conflicto?.recepcionExistente ?: return
        viewModelScope.launch { _effect.send(RegistrarRecepcionEffect.NavegarAResultado(existente.id)) }
    }

    private fun guardar() {
        val estado = _uiState.value
        // Defensa en profundidad, no solo el botón deshabilitado de la UI (mismo criterio que
        // RegistrarCorreccionViewModel/ConfirmarComunicadoViewModel): R-01 no se encola ni se dispara sin
        // conexión (`DATA-006`, trampa #1).
        if (!estado.hayConexion) return

        val litrosCampo = estado.litrosCampoTexto.aDecimalOrNull()
        val errorLitrosCampo = validarLitros(litrosCampo)
        val litrosPlanta = estado.litrosPlantaTexto.aDecimalOrNull()
        val errorLitrosPlanta = validarLitros(litrosPlanta)
        val errorUnidad = if (estado.unidadSeleccionada == null) "Seleccioná una unidad" else null

        if (errorLitrosCampo != null || errorLitrosPlanta != null || errorUnidad != null || estado.errorFecha != null) {
            _uiState.update { it.copy(errorLitrosCampo = errorLitrosCampo, errorLitrosPlanta = errorLitrosPlanta, errorUnidad = errorUnidad) }
            return
        }

        val turnoIngresado = estado.turnoTexto.trim().ifBlank { null }
        _uiState.update { it.copy(enviando = true, mensajeError = null) }

        viewModelScope.launch {
            val datos = NuevaRecepcionPlanta(
                fecha = estado.fecha ?: hoy,
                turno = turnoIngresado,
                unidadId = estado.unidadSeleccionada!!.id,
                litrosCampo = litrosCampo!!,
                litrosPlanta = litrosPlanta!!,
            )
            when (val resultado = registrarRecepcionUseCase(datos)) {
                is ResultadoRegistrarRecepcion.Creada -> {
                    _uiState.update { it.copy(enviando = false) }
                    _effect.send(RegistrarRecepcionEffect.NavegarAResultado(resultado.recepcion.id))
                }
                ResultadoRegistrarRecepcion.YaExiste -> manejarConflicto(datos.unidadId, estado.fecha ?: hoy, turnoIngresado)
                is ResultadoRegistrarRecepcion.Error -> _uiState.update { it.copy(enviando = false, mensajeError = resultado.error.mensaje) }
            }
        }
    }

    /** Una sola búsqueda del registro existente -- nunca reintenta el `POST` (trampa #2). */
    private suspend fun manejarConflicto(unidadId: String, fecha: LocalDate, turnoIngresado: String?) {
        val turnoReal = turnoIngresado ?: TURNO_POR_DEFECTO
        when (val resultado = buscarRecepcionesUseCase(unidadId)) {
            is ResultadoDominio.Exito -> {
                val existente = resultado.datos.firstOrNull { it.fecha == fecha && it.turno == turnoReal }
                _uiState.update { it.copy(enviando = false, conflicto = ConflictoRecepcion(existente, turnoReal)) }
            }
            is ResultadoDominio.Error -> _uiState.update { it.copy(enviando = false, conflicto = ConflictoRecepcion(null, turnoReal)) }
        }
    }

    private fun validarLitros(litros: Decimal?): String? = when {
        litros == null -> "Ingresá una cantidad válida"
        litros.isNegative -> "Los litros no pueden ser negativos"
        digitosEnterosDe(litros) > MAX_DIGITOS_ENTEROS_LITROS -> "Máximo $MAX_DIGITOS_ENTEROS_LITROS dígitos enteros"
        else -> null
    }
}

private fun digitosEnterosDe(valor: Decimal): Int {
    val texto = valor.aTextoConEscala(2)
    return texto.substringBefore('.').removePrefix("-").length
}

private fun String.aDecimalOrNull(): Decimal? = try {
    decimalDesdeTexto(this)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
