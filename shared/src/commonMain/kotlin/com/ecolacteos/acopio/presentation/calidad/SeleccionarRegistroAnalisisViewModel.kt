package com.ecolacteos.acopio.presentation.calidad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.data.repository.ReferenciaRegistroAcopio
import com.ecolacteos.acopio.domain.usecase.ClasificarPadresRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRegistrosDeProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.PadreRegistroAcopioElegible
import com.ecolacteos.acopio.domain.usecase.aReferenciaPadre
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
 * `PROMPT_FASE_08C.md §3.1`/§8` decisión 2, texto literal -- honesto sobre `DATA-014`: no promete que el
 * análisis "se va a enviar cuando la entrega se sincronice" (falso hoy, ver la sección 3 del prompt), y da
 * la salida real (§3.1.2): refrescar el historial del proveedor puede resolver la entrega como ajena.
 */
const val AVISO_ANALISIS_RETENIDO_POR_DEPENDENCIA =
    "Esta entrega todavía no se envió. El análisis se va a guardar, pero puede quedar retenido incluso " +
        "después de que la entrega se sincronice, por una limitación del servidor. Con señal, volvé a " +
        "esta pantalla y tocá \"Actualizar\": si la entrega ya se envió, va a aparecer con su id real y " +
        "el análisis se va a poder enviar sin quedar retenido."

/**
 * Una fila de `C-02`. [aviso] no nulo únicamente en el caso 3 ([PadreRegistroAcopioElegible.PropioPendienteDeSync]) --
 * `DATA-003`: se muestra en la fila, antes de que el usuario elija, nunca después de guardar.
 */
data class ItemSeleccionRegistroUiState(
    val idUi: String,
    val fechaHoraTexto: String,
    val litrosTexto: String,
    val aviso: String? = null,
)

data class SeleccionarRegistroUiState(
    val proveedorId: String,
    val items: List<ItemSeleccionRegistroUiState> = emptyList(),
    val cargando: Boolean = true,
    val hayConexion: Boolean = true,
    val actualizando: Boolean = false,
) {
    val vacio: Boolean get() = !cargando && items.isEmpty()
}

sealed interface SeleccionarRegistroEvent {
    data class ItemSeleccionado(val idUi: String) : SeleccionarRegistroEvent
    data object ActualizarPresionado : SeleccionarRegistroEvent
}

sealed interface SeleccionarRegistroEffect {
    /** Mismo par que persiste `analisis_calidad_local` (`§C-02` invariante) -- exactamente uno no-nulo. */
    data class NavegarACapturar(val registroAcopioUuidCliente: String?, val registroAcopioServerId: String?) : SeleccionarRegistroEffect
}

/**
 * `C-02 · Seleccionar registro de acopio a analizar ★` (Fase 8C, `MOBILE_SCREENS.md §6`) -- la pantalla
 * central de la sub-fase. Decisión 1 del checkpoint: además del refresco automático al abrir (mismo patrón
 * de `HistorialProveedorViewModel`, trampa #2 de `PROMPT_FASE_06.md §10`: una sola vez, nunca por
 * recomposición), expone un "Actualizar" manual **siempre visible** con conexión -- no solo cuando hay
 * filas en el caso 3 -- para que convertir un padre propio recién sincronizado en un padre ajeno resuelto
 * (`§3.1.2`) sea una acción de un toque y predecible, en vez de una que aparece y desaparece según el
 * contenido de la lista.
 */
class SeleccionarRegistroAnalisisViewModel(
    proveedorId: String,
    private val clasificarPadresRegistroAcopioUseCase: ClasificarPadresRegistroAcopioUseCase,
    private val obtenerRegistrosDeProveedorUseCase: ObtenerRegistrosDeProveedorUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeleccionarRegistroUiState(proveedorId = proveedorId))
    val uiState: StateFlow<SeleccionarRegistroUiState> = _uiState.asStateFlow()

    private val _effect = Channel<SeleccionarRegistroEffect>(Channel.BUFFERED)
    val effect: Flow<SeleccionarRegistroEffect> = _effect.receiveAsFlow()

    private var elegibles: Map<String, PadreRegistroAcopioElegible> = emptyMap()

    init {
        clasificarPadresRegistroAcopioUseCase(proveedorId)
            .onEach { lista ->
                elegibles = lista.associateBy { it.idUiDe() }
                val ordenada = lista.sortedByDescending { it.fechaHora }
                _uiState.update { it.copy(cargando = false, items = ordenada.map { elegible -> elegible.aItemUi() }) }
            }
            .launchIn(viewModelScope)

        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { refrescar() }
    }

    fun onEvent(evento: SeleccionarRegistroEvent) {
        when (evento) {
            is SeleccionarRegistroEvent.ItemSeleccionado -> seleccionar(evento.idUi)
            SeleccionarRegistroEvent.ActualizarPresionado -> viewModelScope.launch { refrescar() }
        }
    }

    private fun seleccionar(idUi: String) {
        val elegible = elegibles[idUi] ?: return
        val referencia = elegible.aReferenciaPadre()
        viewModelScope.launch {
            _effect.send(
                SeleccionarRegistroEffect.NavegarACapturar(
                    registroAcopioUuidCliente = (referencia as? ReferenciaRegistroAcopio.Propio)?.uuidCliente,
                    registroAcopioServerId = (referencia as? ReferenciaRegistroAcopio.Ajeno)?.serverId,
                ),
            )
        }
    }

    private suspend fun refrescar() {
        _uiState.update { it.copy(actualizando = true) }
        obtenerRegistrosDeProveedorUseCase(_uiState.value.proveedorId)
        _uiState.update { it.copy(actualizando = false) }
    }

    private fun PadreRegistroAcopioElegible.idUiDe(): String = when (this) {
        is PadreRegistroAcopioElegible.AjenoDisponible -> "ajeno:${referencia.id}"
        is PadreRegistroAcopioElegible.PropioSincronizado -> "propio:${registro.uuidCliente}"
        is PadreRegistroAcopioElegible.PropioPendienteDeSync -> "propio:${registro.uuidCliente}"
    }

    private fun PadreRegistroAcopioElegible.aItemUi(): ItemSeleccionRegistroUiState = ItemSeleccionRegistroUiState(
        idUi = idUiDe(),
        fechaHoraTexto = fechaHora.formateada(),
        litrosTexto = litros.aTextoConEscala(ESCALA_LITROS),
        aviso = if (this is PadreRegistroAcopioElegible.PropioPendienteDeSync) AVISO_ANALISIS_RETENIDO_POR_DEPENDENCIA else null,
    )
}
