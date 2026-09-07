package com.ecolacteos.acopio.presentation.produccion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.data.repository.ReferenciaRegistroAcopio
import com.ecolacteos.acopio.domain.usecase.ClasificarPadresRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRegistrosDeProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.PadreRegistroAcopioElegible
import com.ecolacteos.acopio.domain.usecase.aReferenciaPadre
import com.ecolacteos.acopio.domain.usecase.evaluarRetencionAgregada
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

/**
 * `PROMPT_FASE_08D.md §4.2`/§8` decisión 1, texto literal -- honesto sobre `DATA-014` igual que `8C`
 * (`AVISO_ANALISIS_RETENIDO_POR_DEPENDENCIA`), adaptado al plural, al conteo "N de M" y a que **alcanza
 * una sola** entrega sin sincronizar para retener el lote entero (`§4.1`).
 */
fun avisoLoteRetenidoPorDependencia(pendientesDeSync: Int, total: Int): String =
    "$pendientesDeSync de las $total entregas elegidas todavía no se enviaron. Alcanza con que una sola " +
        "no se haya enviado para que el lote entero quede retenido -- puede seguir retenido incluso " +
        "después de que esas entregas se sincronicen, por una limitación del servidor. Con señal, volvé " +
        "a esta pantalla y tocá \"Actualizar\": las que ya se enviaron van a aparecer con su id real, y " +
        "si ninguna queda sin enviar, el lote se va a poder enviar sin quedar retenido."

/** Una fila de `P-02`. [seleccionado] la controla el usuario -- selección múltiple, a diferencia de `C-02`. */
data class ItemSeleccionLoteUiState(
    val idUi: String,
    val fechaHoraTexto: String,
    val litrosTexto: String,
    val seleccionado: Boolean,
)

data class SeleccionarRegistrosLoteUiState(
    val proveedorId: String,
    val items: List<ItemSeleccionLoteUiState> = emptyList(),
    val cargando: Boolean = true,
    val hayConexion: Boolean = true,
    val actualizando: Boolean = false,
    val totalLitrosSeleccionadoTexto: String = "0.00",
    /** No nulo si al menos una de las seleccionadas es propia sin sincronizar (`§4.1`) -- retención agregada. */
    val avisoRetencion: String? = null,
) {
    val vacio: Boolean get() = !cargando && items.isEmpty()

    /** `@NotEmpty` (`§4`): mínimo 1 registro seleccionado para continuar a `P-03`. */
    val puedeContinuar: Boolean get() = items.any { it.seleccionado }
}

sealed interface SeleccionarRegistrosLoteEvent {
    data class ItemToggled(val idUi: String) : SeleccionarRegistrosLoteEvent
    data object ActualizarPresionado : SeleccionarRegistrosLoteEvent
    data object ContinuarPresionado : SeleccionarRegistrosLoteEvent
}

sealed interface SeleccionarRegistrosLoteEffect {
    data class NavegarACapturar(
        val registroAcopioUuidClientes: List<String>,
        val registroAcopioServerIds: List<String>,
        val totalLitrosTexto: String,
    ) : SeleccionarRegistrosLoteEffect
}

/**
 * `P-02 · Seleccionar registros de acopio para el lote ★` (Fase 8D, `MOBILE_SCREENS.md §7`) -- hermana de
 * `C-02` (`8C`) con selección múltiple: reusa el mismo `ClasificarPadresRegistroAcopioUseCase` tal cual
 * (`§0` del prompt, "no reimplementa la clasificación"), sin heurística de matching (trampa #10).
 */
class SeleccionarRegistrosLoteViewModel(
    proveedorId: String,
    private val clasificarPadresRegistroAcopioUseCase: ClasificarPadresRegistroAcopioUseCase,
    private val obtenerRegistrosDeProveedorUseCase: ObtenerRegistrosDeProveedorUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeleccionarRegistrosLoteUiState(proveedorId = proveedorId))
    val uiState: StateFlow<SeleccionarRegistrosLoteUiState> = _uiState.asStateFlow()

    private val _effect = Channel<SeleccionarRegistrosLoteEffect>(Channel.BUFFERED)
    val effect: Flow<SeleccionarRegistrosLoteEffect> = _effect.receiveAsFlow()

    private var elegibles: Map<String, PadreRegistroAcopioElegible> = emptyMap()
    private var seleccionados: Set<String> = emptySet()

    init {
        clasificarPadresRegistroAcopioUseCase(proveedorId)
            .onEach { lista ->
                elegibles = lista.associateBy { it.idUiDe() }
                // Preserva la selección entre re-emisiones (ej. tras "Actualizar") por idUi -- una fila
                // que ya no está (nunca debería pasar, la lista no borra en masa) sale sola del set.
                seleccionados = seleccionados intersect elegibles.keys
                aplicarEstado(lista.sortedByDescending { it.fechaHora })
            }
            .launchIn(viewModelScope)

        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { refrescar() }
    }

    fun onEvent(evento: SeleccionarRegistrosLoteEvent) {
        when (evento) {
            is SeleccionarRegistrosLoteEvent.ItemToggled -> {
                seleccionados = if (evento.idUi in seleccionados) seleccionados - evento.idUi else seleccionados + evento.idUi
                aplicarEstado(elegibles.values.sortedByDescending { it.fechaHora })
            }
            SeleccionarRegistrosLoteEvent.ActualizarPresionado -> viewModelScope.launch { refrescar() }
            SeleccionarRegistrosLoteEvent.ContinuarPresionado -> continuar()
        }
    }

    private fun continuar() {
        val elegidos = seleccionados.mapNotNull { elegibles[it] }
        if (elegidos.isEmpty()) return
        val referencias = elegidos.map { it.aReferenciaPadre() }
        val uuidClientes = referencias.filterIsInstance<ReferenciaRegistroAcopio.Propio>().map { it.uuidCliente }
        val serverIds = referencias.filterIsInstance<ReferenciaRegistroAcopio.Ajeno>().map { it.serverId }
        val total = elegidos.fold(Decimal.parseString("0")) { acumulado, elegible -> acumulado + elegible.litros }
        viewModelScope.launch {
            _effect.send(
                SeleccionarRegistrosLoteEffect.NavegarACapturar(
                    registroAcopioUuidClientes = uuidClientes,
                    registroAcopioServerIds = serverIds,
                    totalLitrosTexto = total.aTextoConEscala(ESCALA_LITROS),
                ),
            )
        }
    }

    private suspend fun refrescar() {
        _uiState.update { it.copy(actualizando = true) }
        obtenerRegistrosDeProveedorUseCase(_uiState.value.proveedorId)
        _uiState.update { it.copy(actualizando = false) }
    }

    private fun aplicarEstado(elegiblesOrdenados: List<PadreRegistroAcopioElegible>) {
        val seleccionActual = elegiblesOrdenados.filter { it.idUiDe() in seleccionados }
        val retencion = evaluarRetencionAgregada(seleccionActual)
        val totalLitros = seleccionActual.fold(Decimal.parseString("0")) { acumulado, elegible -> acumulado + elegible.litros }

        _uiState.update {
            it.copy(
                cargando = false,
                items = elegiblesOrdenados.map { elegible -> elegible.aItemUi() },
                totalLitrosSeleccionadoTexto = totalLitros.aTextoConEscala(ESCALA_LITROS),
                avisoRetencion = if (retencion.seraRetenido) avisoLoteRetenidoPorDependencia(retencion.pendientesDeSync, retencion.total) else null,
            )
        }
    }

    private fun PadreRegistroAcopioElegible.idUiDe(): String = when (this) {
        is PadreRegistroAcopioElegible.AjenoDisponible -> "ajeno:${referencia.id}"
        is PadreRegistroAcopioElegible.PropioSincronizado -> "propio:${registro.uuidCliente}"
        is PadreRegistroAcopioElegible.PropioPendienteDeSync -> "propio:${registro.uuidCliente}"
    }

    private fun PadreRegistroAcopioElegible.aItemUi(): ItemSeleccionLoteUiState = ItemSeleccionLoteUiState(
        idUi = idUiDe(),
        fechaHoraTexto = fechaHora.formateada(),
        litrosTexto = litros.aTextoConEscala(ESCALA_LITROS),
        seleccionado = idUiDe() in seleccionados,
    )
}
