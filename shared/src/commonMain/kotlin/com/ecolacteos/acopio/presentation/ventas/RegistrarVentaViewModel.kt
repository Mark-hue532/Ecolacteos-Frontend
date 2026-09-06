package com.ecolacteos.acopio.presentation.ventas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.core.decimalDesdeTexto
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.data.repository.NuevaVenta
import com.ecolacteos.acopio.domain.model.TipoQueso
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearVentaUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ResultadoCrearVenta
import com.ecolacteos.acopio.network.jsonApi
import com.ecolacteos.acopio.presentation.formateada
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.time.Clock

/** Clave de `borrador_formulario` para esta pantalla (`MOBILE_SCREENS.md §3.4`). */
private const val PANTALLA_BORRADOR = "registrar_venta"
private const val DEBOUNCE_BORRADOR_MS = 500L

/** `MOBILE_DATA_MAPPING.md §5.5`: escala real de `precioUnitario`/`total` en Venta. */
private const val ESCALA_PRECIO = 2

/** Selector cerrado de exactamente 3 opciones (`DATA-010`) -- `UNKNOWN` nunca aparece en esta lista. */
val OPCIONES_TIPO_CLIENTE: List<TipoClienteVenta> =
    listOf(TipoClienteVenta.MAYORISTA, TipoClienteVenta.PROVEEDOR, TipoClienteVenta.PUBLICO)

data class RegistrarVentaUiState(
    val fechaTexto: String = "",
    val tipoClienteSeleccionado: TipoClienteVenta? = null,
    val tiposQueso: List<TipoQueso> = emptyList(),
    val tipoQuesoSeleccionado: TipoQueso? = null,
    val cantidadTexto: String = "",
    val precioUnitarioTexto: String = "",
    /** Subtotal de referencia -- nunca rotulado "Total" (`§8`, `MOBILE_DATA_MAPPING.md §5.5`). */
    val subtotalEstimadoTexto: String? = null,
    val errorCantidad: String? = null,
    val errorPrecio: String? = null,
    val errorTipoCliente: String? = null,
    val errorTipoQueso: String? = null,
    val errorGeneral: String? = null,
    val guardando: Boolean = false,
    val hayConexion: Boolean = true,
    val hayBorradorParaRetomar: Boolean = false,
) {
    val puedeGuardar: Boolean
        get() = !guardando && cantidadTexto.isNotBlank() && precioUnitarioTexto.isNotBlank() &&
            tipoClienteSeleccionado != null && tipoQuesoSeleccionado != null
}

sealed interface RegistrarVentaEvent {
    data class TipoClienteCambio(val valor: TipoClienteVenta) : RegistrarVentaEvent
    data class TipoQuesoCambio(val valor: TipoQueso) : RegistrarVentaEvent
    data class CantidadCambio(val texto: String) : RegistrarVentaEvent
    data class PrecioCambio(val texto: String) : RegistrarVentaEvent
    data object GuardarPresionado : RegistrarVentaEvent
    data object RetomarBorradorPresionado : RegistrarVentaEvent
    data object DescartarBorradorPresionado : RegistrarVentaEvent
}

sealed interface RegistrarVentaEffect {
    /** `§2.1` regla 3: vuelve hacia atrás con `Snackbar` "Guardado -- se enviará cuando haya señal". */
    data object GuardadoConExito : RegistrarVentaEffect
}

@Serializable
private data class BorradorVenta(
    val tipoCliente: String?,
    val tipoQuesoId: String?,
    val cantidadTexto: String,
    val precioUnitarioTexto: String,
)

/**
 * `V-02 · Registrar venta ★` (`MOBILE_SCREENS.md §8`). `fecha` se fija a hoy al abrir la pantalla y no es
 * editable en esta fase -- no hay selector de fecha entre los 10 componentes de `§13`, y el contrato solo
 * exige que sea una fecha válida (`MOBILE_DATA_MAPPING.md §5.5`), no que sea editable (decisión de esta
 * fase, ver checkpoint).
 */
class RegistrarVentaViewModel(
    private val crearVentaUseCase: CrearVentaUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
    private val borradorFormularioUseCase: BorradorFormularioUseCase,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val fecha: LocalDate = ahoraComoFechaHora(reloj, zona).date

    private val _uiState = MutableStateFlow(RegistrarVentaUiState(fechaTexto = fecha.formateada()))
    val uiState: StateFlow<RegistrarVentaUiState> = _uiState.asStateFlow()

    private val _effect = Channel<RegistrarVentaEffect>(Channel.BUFFERED)
    val effect: Flow<RegistrarVentaEffect> = _effect.receiveAsFlow()

    private var borradorPendiente: BorradorVenta? = null
    private var jobDebounceBorrador: Job? = null

    init {
        observarCatalogosUseCase.tiposQueso()
            .onEach { tipos -> _uiState.update { it.copy(tiposQueso = tipos) } }
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

    fun onEvent(evento: RegistrarVentaEvent) {
        when (evento) {
            is RegistrarVentaEvent.TipoClienteCambio -> {
                _uiState.update { it.copy(tipoClienteSeleccionado = evento.valor, errorTipoCliente = null) }
                recalcularSubtotal()
                programarGuardadoDeBorrador()
            }
            is RegistrarVentaEvent.TipoQuesoCambio -> {
                _uiState.update { it.copy(tipoQuesoSeleccionado = evento.valor, errorTipoQueso = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarVentaEvent.CantidadCambio -> {
                _uiState.update { it.copy(cantidadTexto = evento.texto, errorCantidad = null) }
                recalcularSubtotal()
                programarGuardadoDeBorrador()
            }
            is RegistrarVentaEvent.PrecioCambio -> {
                _uiState.update { it.copy(precioUnitarioTexto = evento.texto, errorPrecio = null) }
                recalcularSubtotal()
                programarGuardadoDeBorrador()
            }
            RegistrarVentaEvent.GuardarPresionado -> guardar()
            RegistrarVentaEvent.RetomarBorradorPresionado -> retomarBorrador()
            RegistrarVentaEvent.DescartarBorradorPresionado -> {
                borradorFormularioUseCase.descartar(PANTALLA_BORRADOR)
                borradorPendiente = null
                _uiState.update { it.copy(hayBorradorParaRetomar = false) }
            }
        }
    }

    private fun retomarBorrador() {
        val borrador = borradorPendiente ?: return
        val tipoCliente = borrador.tipoCliente?.let { nombre -> OPCIONES_TIPO_CLIENTE.firstOrNull { it.name == nombre } }
        val tipoQueso = _uiState.value.tiposQueso.firstOrNull { it.id == borrador.tipoQuesoId }
        _uiState.update {
            it.copy(
                tipoClienteSeleccionado = tipoCliente,
                tipoQuesoSeleccionado = tipoQueso,
                cantidadTexto = borrador.cantidadTexto,
                precioUnitarioTexto = borrador.precioUnitarioTexto,
                hayBorradorParaRetomar = false,
            )
        }
        recalcularSubtotal()
        borradorPendiente = null
    }

    private fun recalcularSubtotal() {
        val estado = _uiState.value
        val cantidad = estado.cantidadTexto.toIntOrNull()
        val precio = estado.precioUnitarioTexto.toDecimalOrNull()
        val subtotal = if (cantidad != null && cantidad >= 1 && precio != null) {
            "S/ ${precio.times(cantidad).aTextoConEscala(ESCALA_PRECIO)} (estimado)"
        } else {
            null
        }
        _uiState.update { it.copy(subtotalEstimadoTexto = subtotal) }
    }

    private fun programarGuardadoDeBorrador() {
        jobDebounceBorrador?.cancel()
        jobDebounceBorrador = viewModelScope.launch {
            delay(DEBOUNCE_BORRADOR_MS)
            val estado = _uiState.value
            val borrador = BorradorVenta(
                tipoCliente = estado.tipoClienteSeleccionado?.name,
                tipoQuesoId = estado.tipoQuesoSeleccionado?.id,
                cantidadTexto = estado.cantidadTexto,
                precioUnitarioTexto = estado.precioUnitarioTexto,
            )
            borradorFormularioUseCase.guardar(PANTALLA_BORRADOR, jsonApi.encodeToString(BorradorVenta.serializer(), borrador))
        }
    }

    private fun guardar() {
        val estado = _uiState.value
        val cantidad = estado.cantidadTexto.toIntOrNull()
        val precio = estado.precioUnitarioTexto.toDecimalOrNull()

        val errorCantidad = when {
            cantidad == null -> "Ingresá una cantidad válida"
            cantidad < 1 -> "La cantidad mínima es 1"
            else -> null
        }
        val errorPrecio = if (precio == null || precio.isNegative) "Ingresá un precio válido (mínimo 0)" else null
        val errorTipoCliente = if (estado.tipoClienteSeleccionado == null) "Seleccioná un tipo de cliente" else null
        val errorTipoQueso = if (estado.tipoQuesoSeleccionado == null) "Seleccioná un tipo de queso" else null

        if (errorCantidad != null || errorPrecio != null || errorTipoCliente != null || errorTipoQueso != null) {
            _uiState.update {
                it.copy(
                    errorCantidad = errorCantidad,
                    errorPrecio = errorPrecio,
                    errorTipoCliente = errorTipoCliente,
                    errorTipoQueso = errorTipoQueso,
                )
            }
            return
        }

        _uiState.update { it.copy(guardando = true, errorGeneral = null) }
        viewModelScope.launch {
            val resultado = crearVentaUseCase(
                NuevaVenta(
                    fecha = fecha,
                    tipoCliente = estado.tipoClienteSeleccionado!!,
                    tipoQuesoId = estado.tipoQuesoSeleccionado!!.id,
                    cantidad = cantidad!!,
                    precioUnitario = precio!!,
                ),
            )
            when (resultado) {
                is ResultadoCrearVenta.Creada -> {
                    borradorFormularioUseCase.descartar(PANTALLA_BORRADOR)
                    _uiState.update { it.copy(guardando = false) }
                    _effect.send(RegistrarVentaEffect.GuardadoConExito)
                }
                ResultadoCrearVenta.TipoClienteInvalido -> _uiState.update {
                    it.copy(guardando = false, errorTipoCliente = "Seleccioná un tipo de cliente válido")
                }
            }
        }
    }
}

/**
 * `bignum` no documenta un tipo de excepción propio y estable para texto inválido -- se captura `Exception`
 * en vez de adivinar el tipo exacto (`NumberFormatException` en unas plataformas, otro en Kotlin/Native).
 * Nunca deja pasar `CancellationException`, coherente con el resto del proyecto (ver `ApiClient.kt`).
 */
private fun String.toDecimalOrNull(): Decimal? = try {
    decimalDesdeTexto(this)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

private fun String.aBorradorOrNull(): BorradorVenta? = try {
    jsonApi.decodeFromString(BorradorVenta.serializer(), this)
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}
