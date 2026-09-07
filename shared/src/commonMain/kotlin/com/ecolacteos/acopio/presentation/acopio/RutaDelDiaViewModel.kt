package com.ecolacteos.acopio.presentation.acopio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.domain.model.RutaProveedorOrden
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarProveedoresVisitadosHoyUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRutaDelDiaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerZonaAsignadaUseCase
import com.ecolacteos.acopio.presentation.formateada
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

/** Una fila de `A-01` -- `horaEstimadaTexto` es `null` cuando `horaEstimada` es nula: nunca `"--:--"`. */
data class ItemRutaUiState(
    val proveedorId: String,
    val proveedorNombre: String,
    val orden: Int,
    val horaEstimadaTexto: String?,
    val visitadoHoy: Boolean,
)

/**
 * `A-01` (`MOBILE_SCREENS.md §5`). [vacioSinRuta]/[vacioNuncaDescargada] usan [hayConexion] como proxy de
 * si el último intento de `obtenerRutaDelDia` fue contra la red o degradó a cache -- decisión del
 * checkpoint: el contrato de `CatalogoRepository.obtenerRutaDelDia` no distingue "el servidor respondió
 * vacío" de "no se pudo consultar", así que se aproxima con la señal de conectividad en el momento de
 * cargar, en vez de agregarle ese estado al Repository para un caso de uso tan acotado.
 */
data class RutaDelDiaUiState(
    val cargando: Boolean = true,
    /** `false` = la heurística de `ObtenerZonaAsignadaUseCase` (`DATA-016`) no pudo resolver una sola zona. */
    val zonaDeterminada: Boolean = true,
    val items: List<ItemRutaUiState> = emptyList(),
    val hayConexion: Boolean = true,
) {
    val vacioSinRuta: Boolean get() = zonaDeterminada && !cargando && items.isEmpty() && hayConexion
    val vacioNuncaDescargada: Boolean get() = zonaDeterminada && !cargando && items.isEmpty() && !hayConexion
}

sealed interface RutaDelDiaEvent {
    data object EscanearQrPresionado : RutaDelDiaEvent
    data object BuscarProveedorPresionado : RutaDelDiaEvent
    data class ProveedorSeleccionado(val proveedorId: String) : RutaDelDiaEvent
    data object ReintentarPresionado : RutaDelDiaEvent
}

sealed interface RutaDelDiaEffect {
    data object NavegarAEscanearQr : RutaDelDiaEffect
    data object NavegarABuscarProveedor : RutaDelDiaEffect
    data class NavegarARegistrar(val proveedorId: String) : RutaDelDiaEffect
}

/** Resultado de resolver zona + ruta una sola vez (`suspend`), combinado luego con "visitados hoy" (reactivo). */
private sealed interface EstadoBase {
    data object Cargando : EstadoBase
    data object SinZona : EstadoBase
    data class ConRuta(val items: List<RutaProveedorOrden>) : EstadoBase
}

class RutaDelDiaViewModel(
    private val obtenerRutaDelDiaUseCase: ObtenerRutaDelDiaUseCase,
    private val obtenerZonaAsignadaUseCase: ObtenerZonaAsignadaUseCase,
    private val observarProveedoresVisitadosHoyUseCase: ObservarProveedoresVisitadosHoyUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RutaDelDiaUiState())
    val uiState: StateFlow<RutaDelDiaUiState> = _uiState.asStateFlow()

    private val _effect = Channel<RutaDelDiaEffect>(Channel.BUFFERED)
    val effect: Flow<RutaDelDiaEffect> = _effect.receiveAsFlow()

    private val base = MutableStateFlow<EstadoBase>(EstadoBase.Cargando)

    init {
        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)

        // Combinado directo en init (no anidado dentro de otro launch): igual criterio que el resto de los
        // ViewModel de la app -- .launchIn nunca vive adentro de un viewModelScope.launch { } ajeno, para
        // que colecte de inmediato bajo UnconfinedTestDispatcher (ver checkpoint, "Decisiones tomadas").
        combine(base, observarProveedoresVisitadosHoyUseCase()) { estadoBase, visitados -> estadoBase to visitados }
            .onEach { (estadoBase, visitados) -> _uiState.update { it.aplicar(estadoBase, visitados) } }
            .launchIn(viewModelScope)

        cargar()
    }

    fun onEvent(evento: RutaDelDiaEvent) {
        when (evento) {
            RutaDelDiaEvent.EscanearQrPresionado ->
                viewModelScope.launch { _effect.send(RutaDelDiaEffect.NavegarAEscanearQr) }
            RutaDelDiaEvent.BuscarProveedorPresionado ->
                viewModelScope.launch { _effect.send(RutaDelDiaEffect.NavegarABuscarProveedor) }
            is RutaDelDiaEvent.ProveedorSeleccionado ->
                viewModelScope.launch { _effect.send(RutaDelDiaEffect.NavegarARegistrar(evento.proveedorId)) }
            RutaDelDiaEvent.ReintentarPresionado -> cargar()
        }
    }

    private fun cargar() {
        base.value = EstadoBase.Cargando
        viewModelScope.launch {
            val zonaId = obtenerZonaAsignadaUseCase()
            base.value = if (zonaId == null) EstadoBase.SinZona else EstadoBase.ConRuta(obtenerRutaDelDiaUseCase(zonaId))
        }
    }

    private fun RutaDelDiaUiState.aplicar(estadoBase: EstadoBase, visitados: Set<String>): RutaDelDiaUiState = when (estadoBase) {
        EstadoBase.Cargando -> copy(cargando = true)
        EstadoBase.SinZona -> copy(cargando = false, zonaDeterminada = false, items = emptyList())
        is EstadoBase.ConRuta -> copy(
            cargando = false,
            zonaDeterminada = true,
            items = estadoBase.items.sortedBy { it.orden }.map { it.aItemUi(visitados) },
        )
    }

    private fun RutaProveedorOrden.aItemUi(visitados: Set<String>): ItemRutaUiState = ItemRutaUiState(
        proveedorId = proveedorId,
        proveedorNombre = proveedorNombre,
        orden = orden,
        horaEstimadaTexto = horaEstimada?.formateada(),
        visitadoHoy = proveedorId in visitados,
    )
}
