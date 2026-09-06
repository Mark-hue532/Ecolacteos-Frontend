package com.ecolacteos.acopio.presentation.ventas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.model.Venta
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarVentasDelDiaUseCase
import com.ecolacteos.acopio.domain.usecase.estadoSincronizacionDe
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
import kotlinx.coroutines.launch

/** Escala de `precioUnitario`/`total` de Venta (`MOBILE_SCREENS.md §10.1`). */
private const val ESCALA_MONEDA = 2

/** Una fila de `V-01` -- nunca lleva un campo "total": ver la nota de [subtotalEstimadoTexto]. */
data class ItemVentaUiState(
    val uuidCliente: String,
    val fechaTexto: String,
    val tipoClienteTexto: String,
    val cantidad: Int,
    val precioUnitarioTexto: String,
    /**
     * `cantidad × precioUnitario` calculado en el cliente, **nunca** rotulado "Total" (`§8`): el `total`
     * real es una columna `GENERATED` de Postgres que este dispositivo no conoce hasta que la fila
     * sincronice y, aun sincronizada, `venta_local` no lo persiste (ver `VentaRepository.obtenerDetalle` y
     * el hallazgo nuevo del checkpoint de la Fase 7). `V-03` sí muestra el valor real del servidor.
     */
    val subtotalEstimadoTexto: String,
    val estadoSync: EstadoSincronizacion,
)

data class HomeVentasUiState(
    val ventas: List<ItemVentaUiState> = emptyList(),
    val cargando: Boolean = true,
    val hayConexion: Boolean = true,
) {
    val vacio: Boolean get() = !cargando && ventas.isEmpty()
}

sealed interface HomeVentasEvent {
    data object RegistrarVentaPresionado : HomeVentasEvent
    data class VentaSeleccionada(val uuidCliente: String) : HomeVentasEvent
}

sealed interface HomeVentasEffect {
    data object NavegarARegistrarVenta : HomeVentasEffect
    data class NavegarADetalle(val uuidCliente: String) : HomeVentasEffect
}

/**
 * `V-01 Home ventas` (`MOBILE_SCREENS.md §8`). Resuelve la contradicción de `§12` #1 a favor de `§8`
 * ("Modo offline OK · Fuente `venta_local`"), no de la fila de trazabilidad de `§18` -- ver el checkpoint
 * de la Fase 7 para la justificación completa y la corrección que le falta al documento. Por eso esta
 * pantalla **no** llama a `GET /api/ventas`: observa `ObservarVentasDelDiaUseCase`, 100% local.
 */
class HomeVentasViewModel(
    private val observarVentasDelDiaUseCase: ObservarVentasDelDiaUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeVentasUiState())
    val uiState: StateFlow<HomeVentasUiState> = _uiState.asStateFlow()

    private val _effect = Channel<HomeVentasEffect>(Channel.BUFFERED)
    val effect: Flow<HomeVentasEffect> = _effect.receiveAsFlow()

    init {
        combine(observarVentasDelDiaUseCase(), observarConectividadUseCase()) { ventas, conectado -> ventas to conectado }
            .onEach { (ventas, conectado) ->
                _uiState.value = HomeVentasUiState(
                    ventas = ventas.map { it.aItemUi() },
                    cargando = false,
                    hayConexion = conectado,
                )
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(evento: HomeVentasEvent) {
        when (evento) {
            HomeVentasEvent.RegistrarVentaPresionado ->
                viewModelScope.launch { _effect.send(HomeVentasEffect.NavegarARegistrarVenta) }
            is HomeVentasEvent.VentaSeleccionada ->
                viewModelScope.launch { _effect.send(HomeVentasEffect.NavegarADetalle(evento.uuidCliente)) }
        }
    }

    private fun Venta.aItemUi(): ItemVentaUiState {
        val subtotal: Decimal = precioUnitario.times(cantidad)
        return ItemVentaUiState(
            uuidCliente = uuidCliente,
            fechaTexto = fecha.formateada(),
            tipoClienteTexto = tipoCliente.name,
            cantidad = cantidad,
            precioUnitarioTexto = "S/ ${precioUnitario.aTextoConEscala(ESCALA_MONEDA)}",
            subtotalEstimadoTexto = "S/ ${subtotal.aTextoConEscala(ESCALA_MONEDA)}",
            estadoSync = estadoSincronizacionDe(syncStatus, syncError, nextAttemptAt),
        )
    }
}
