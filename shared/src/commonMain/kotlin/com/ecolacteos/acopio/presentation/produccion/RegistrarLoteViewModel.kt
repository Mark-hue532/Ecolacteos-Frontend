package com.ecolacteos.acopio.presentation.produccion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.core.decimalDesdeTexto
import com.ecolacteos.acopio.data.repository.NuevoLoteProduccion
import com.ecolacteos.acopio.data.repository.ReferenciaRegistroAcopio
import com.ecolacteos.acopio.data.repository.ResultadoCrearHijo
import com.ecolacteos.acopio.domain.model.TipoQueso
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearLoteProduccionUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.network.jsonApi
import com.ecolacteos.acopio.presentation.formateada
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

private const val PANTALLA_BORRADOR = "registrar_lote_produccion"
private const val DEBOUNCE_BORRADOR_MS = 500L
private const val MAX_DIGITOS_ENTEROS_LITROS_USADOS = 7 // precision=9, scale=2 -> 9-2

/**
 * `RegistrarLoteUiState` (`MOBILE_SCREENS.md §7`, `C-03`). `fechaTexto` se fija a "hoy" al abrir -- mismo
 * criterio que `A-04`/`C-03` (§5 del prompt: "no hay selector de fecha entre los 10 componentes de §13").
 * [totalLitrosSeleccionadoTexto] es el total de `P-02`, **solo informativo** (`§4.3`, trampa #3): nunca se
 * autocompleta en [litrosUsadosTexto]. **Sin `rendimientoPct`**: lo calcula el servidor (trampa #6).
 */
data class RegistrarLoteUiState(
    val fecha: LocalDate? = null,
    val fechaTexto: String = "",
    val totalLitrosSeleccionadoTexto: String = "0.00",
    val tiposQueso: List<TipoQueso> = emptyList(),
    val tipoQuesoSeleccionado: TipoQueso? = null,
    val litrosUsadosTexto: String = "",
    val unidadesObtenidasTexto: String = "",
    val errorTipoQueso: String? = null,
    val errorLitrosUsados: String? = null,
    val errorUnidadesObtenidas: String? = null,
    val errorFecha: String? = null,
    val guardando: Boolean = false,
    val hayConexion: Boolean = true,
    val hayBorradorParaRetomar: Boolean = false,
    val mensajePadreNoResoluble: String? = null,
) {
    val puedeGuardar: Boolean
        get() = !guardando && tipoQuesoSeleccionado != null && litrosUsadosTexto.isNotBlank() && unidadesObtenidasTexto.isNotBlank() &&
            errorLitrosUsados == null && errorUnidadesObtenidas == null && errorFecha == null
}

sealed interface RegistrarLoteEvent {
    data class TipoQuesoCambio(val valor: TipoQueso) : RegistrarLoteEvent
    data class LitrosUsadosCambio(val texto: String) : RegistrarLoteEvent
    data class UnidadesObtenidasCambio(val texto: String) : RegistrarLoteEvent
    /** Sin selector de fecha entre los componentes de `§13` -- el evento existe para que la validación sea
     * testeable sin esperar a ese componente (mismo criterio que `RegistrarAcopioViewModel.FechaHoraCambio`). */
    data class FechaCambio(val valor: LocalDate) : RegistrarLoteEvent
    data object GuardarPresionado : RegistrarLoteEvent
    data object RetomarBorradorPresionado : RegistrarLoteEvent
    data object DescartarBorradorPresionado : RegistrarLoteEvent
}

sealed interface RegistrarLoteEffect {
    /** `§2.1` regla 3: vuelve atrás con `Snackbar`. */
    data object GuardadoConExito : RegistrarLoteEffect
}

@Serializable
private data class BorradorLote(val tipoQuesoId: String?, val litrosUsadosTexto: String, val unidadesObtenidasTexto: String)

/**
 * `P-03 · Registrar lote de producción ★` (Fase 8D, `MOBILE_SCREENS.md §7`) -- OFFLINE-FIRST con
 * dependencia (`§18.1`), hermana de `RegistrarAnalisisViewModel` (`8C`). [registroAcopioUuidClientes]/
 * [registroAcopioServerIds] vienen de `P-02`, ya resueltos -- no editables acá (`§0` del prompt).
 */
class RegistrarLoteViewModel(
    registroAcopioUuidClientes: List<String> = emptyList(),
    registroAcopioServerIds: List<String> = emptyList(),
    totalLitrosSeleccionadoTexto: String = "0.00",
    private val crearLoteProduccionUseCase: CrearLoteProduccionUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
    private val borradorFormularioUseCase: BorradorFormularioUseCase,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val referencias: List<ReferenciaRegistroAcopio> =
        registroAcopioUuidClientes.map { ReferenciaRegistroAcopio.Propio(it) } +
            registroAcopioServerIds.map { ReferenciaRegistroAcopio.Ajeno(it) }

    init {
        require(referencias.isNotEmpty()) { "P-03 requiere al menos un registro de acopio -- viene de P-02 (@NotEmpty)" }
    }

    private val hoy: LocalDate = ahoraComoFechaHora(reloj, zona).date

    private val _uiState = MutableStateFlow(
        RegistrarLoteUiState(fecha = hoy, fechaTexto = hoy.formateada(), totalLitrosSeleccionadoTexto = totalLitrosSeleccionadoTexto),
    )
    val uiState: StateFlow<RegistrarLoteUiState> = _uiState.asStateFlow()

    private val _effect = Channel<RegistrarLoteEffect>(Channel.BUFFERED)
    val effect: Flow<RegistrarLoteEffect> = _effect.receiveAsFlow()

    private var borradorPendiente: BorradorLote? = null
    private var jobDebounceBorrador: Job? = null

    init {
        observarCatalogosUseCase.tiposQueso()
            .onEach { tiposQueso ->
                val soloActivos = tiposQueso.filter { it.activo } // defensivo -- el server ya filtra (§5, trampa: no es funcional hoy)
                _uiState.update { it.copy(tiposQueso = soloActivos) }
                val idPreseleccionado = borradorPendiente?.tipoQuesoId
                if (idPreseleccionado != null) {
                    soloActivos.firstOrNull { it.id == idPreseleccionado }?.let { encontrado ->
                        _uiState.update { it.copy(tipoQuesoSeleccionado = encontrado) }
                    }
                }
            }
            .launchIn(viewModelScope)

        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)

        val payload = borradorFormularioUseCase.obtener(PANTALLA_BORRADOR)
        if (payload != null) {
            borradorPendiente = payload.aBorradorOrNull()
            if (borradorPendiente != null) _uiState.update { it.copy(hayBorradorParaRetomar = true) }
        }
    }

    fun onEvent(evento: RegistrarLoteEvent) {
        when (evento) {
            is RegistrarLoteEvent.TipoQuesoCambio -> {
                _uiState.update { it.copy(tipoQuesoSeleccionado = evento.valor, errorTipoQueso = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarLoteEvent.LitrosUsadosCambio -> {
                _uiState.update { it.copy(litrosUsadosTexto = evento.texto, errorLitrosUsados = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarLoteEvent.UnidadesObtenidasCambio -> {
                _uiState.update { it.copy(unidadesObtenidasTexto = evento.texto, errorUnidadesObtenidas = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarLoteEvent.FechaCambio -> validarYAplicarFecha(evento.valor)
            RegistrarLoteEvent.GuardarPresionado -> guardar()
            RegistrarLoteEvent.RetomarBorradorPresionado -> retomarBorrador()
            RegistrarLoteEvent.DescartarBorradorPresionado -> {
                borradorFormularioUseCase.descartar(PANTALLA_BORRADOR)
                borradorPendiente = null
                _uiState.update { it.copy(hayBorradorParaRetomar = false) }
            }
        }
    }

    private fun validarYAplicarFecha(nuevaFecha: LocalDate) {
        val error = if (nuevaFecha > hoy) "La fecha no puede ser futura" else null
        _uiState.update { it.copy(fecha = nuevaFecha, fechaTexto = nuevaFecha.formateada(), errorFecha = error) }
    }

    private fun retomarBorrador() {
        val borrador = borradorPendiente ?: return
        val estado = _uiState.value
        val tipoQueso = estado.tiposQueso.firstOrNull { it.id == borrador.tipoQuesoId }
        _uiState.update {
            it.copy(
                tipoQuesoSeleccionado = tipoQueso,
                litrosUsadosTexto = borrador.litrosUsadosTexto,
                unidadesObtenidasTexto = borrador.unidadesObtenidasTexto,
                hayBorradorParaRetomar = false,
            )
        }
        borradorPendiente = null
    }

    private fun programarGuardadoDeBorrador() {
        jobDebounceBorrador?.cancel()
        jobDebounceBorrador = viewModelScope.launch {
            delay(DEBOUNCE_BORRADOR_MS)
            val estado = _uiState.value
            val borrador = BorradorLote(
                tipoQuesoId = estado.tipoQuesoSeleccionado?.id,
                litrosUsadosTexto = estado.litrosUsadosTexto,
                unidadesObtenidasTexto = estado.unidadesObtenidasTexto,
            )
            borradorFormularioUseCase.guardar(PANTALLA_BORRADOR, jsonApi.encodeToString(BorradorLote.serializer(), borrador))
        }
    }

    private fun guardar() {
        val estado = _uiState.value

        val errorTipoQueso = if (estado.tipoQuesoSeleccionado == null) "Seleccioná un tipo de queso" else null
        val litros = estado.litrosUsadosTexto.aDecimalOrNull()
        val errorLitros = when {
            litros == null -> "Ingresá una cantidad válida"
            litros.isNegative -> "Los litros usados no pueden ser negativos" // 0 es válido -- trampa #5
            digitosEnterosDe(litros) > MAX_DIGITOS_ENTEROS_LITROS_USADOS -> "Máximo $MAX_DIGITOS_ENTEROS_LITROS_USADOS dígitos enteros"
            else -> null
        }
        val unidades = estado.unidadesObtenidasTexto.trim().toIntOrNull()
        val errorUnidades = when {
            unidades == null -> "Ingresá un número entero válido"
            unidades < 0 -> "Las unidades obtenidas no pueden ser negativas" // 0 es válido -- trampa #4
            else -> null
        }

        if (errorTipoQueso != null || errorLitros != null || errorUnidades != null || estado.errorFecha != null) {
            _uiState.update { it.copy(errorTipoQueso = errorTipoQueso, errorLitrosUsados = errorLitros, errorUnidadesObtenidas = errorUnidades) }
            return
        }

        _uiState.update { it.copy(guardando = true) }
        val datos = NuevoLoteProduccion(
            fecha = estado.fecha ?: hoy,
            tipoQuesoId = estado.tipoQuesoSeleccionado!!.id,
            litrosUsados = litros!!,
            unidadesObtenidas = unidades!!,
            referenciasRegistros = referencias,
        )

        viewModelScope.launch {
            when (crearLoteProduccionUseCase(datos)) {
                is ResultadoCrearHijo.Creado -> {
                    borradorFormularioUseCase.descartar(PANTALLA_BORRADOR)
                    _uiState.update { it.copy(guardando = false) }
                    _effect.send(RegistrarLoteEffect.GuardadoConExito)
                }
                // Defensivo -- ver el comentario equivalente en RegistrarAnalisisViewModel (8C).
                ResultadoCrearHijo.PadreAjenoNoResolubleSinConectividad -> _uiState.update {
                    it.copy(guardando = false, mensajePadreNoResoluble = "No pudimos confirmar esas entregas sin conexión. Probá de nuevo con señal.")
                }
            }
        }
    }
}

/** Cuenta dígitos enteros vía texto formateado -- nunca por magnitud numérica (evita pasar por `Double`). */
private fun digitosEnterosDe(valor: Decimal): Int {
    val texto = valor.aTextoConEscala(2)
    val parteEntera = texto.substringBefore('.').removePrefix("-")
    return parteEntera.length
}

private fun String.aDecimalOrNull(): Decimal? = try {
    decimalDesdeTexto(this)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

private fun String.aBorradorOrNull(): BorradorLote? = try {
    jsonApi.decodeFromString(BorradorLote.serializer(), this)
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}
