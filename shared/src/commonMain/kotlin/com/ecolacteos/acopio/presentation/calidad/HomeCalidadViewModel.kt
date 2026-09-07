package com.ecolacteos.acopio.presentation.calidad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.usecase.EntregaConEstadoAnalisis
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarEntregasConEstadoAnalisisUseCase
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

private const val ESCALA_LITROS = 2

/** Una fila de `C-01`. [proveedorId] es `null` en el caso raro de `RegistroAcopioReferencia.proveedorId` ausente (§6, no se produce hoy en la práctica -- ver checkpoint), y entonces la fila no navega. */
data class ItemEntregaCalidadUiState(
    val registroAcopioId: String,
    val proveedorId: String?,
    val proveedorNombreTexto: String,
    val fechaHoraTexto: String,
    val litrosTexto: String,
    val tieneAnalisis: Boolean,
)

data class HomeCalidadUiState(
    val items: List<ItemEntregaCalidadUiState> = emptyList(),
    val cargando: Boolean = true,
    val hayConexion: Boolean = true,
) {
    val vacio: Boolean get() = !cargando && items.isEmpty()
}

sealed interface HomeCalidadEvent {
    /** Entrega ya analizada -- va directo a `C-04`. */
    data class EntregaAnalizadaPresionada(val registroAcopioId: String) : HomeCalidadEvent

    /** Entrega sin analizar -- va a `C-02`, con su proveedor ya conocido (no requiere buscarlo de nuevo). */
    data class EntregaSinAnalizarPresionada(val proveedorId: String) : HomeCalidadEvent

    data object AnalizarNuevaEntregaPresionado : HomeCalidadEvent
}

sealed interface HomeCalidadEffect {
    data class NavegarADetalleAnalisis(val registroAcopioId: String) : HomeCalidadEffect
    data class NavegarASeleccionarRegistro(val proveedorId: String) : HomeCalidadEffect
    data object NavegarABuscarProveedor : HomeCalidadEffect
}

/**
 * `C-01 · Home calidad` (Fase 8C, `MOBILE_SCREENS.md §6`, offline OK). Fuente literal del documento:
 * `registro_acopio_cache` + `analisis_calidad_local` (no `registro_acopio_local` -- ver
 * `ObservarEntregasConEstadoAnalisisUseCase`). `proveedorNombreTexto` resuelve contra el catálogo local
 * cuando la fila viene de un DTO resumen (`DATA-013`: `RegistroAcopioReferencia.proveedorNombre` nulo).
 */
class HomeCalidadViewModel(
    private val observarEntregasConEstadoAnalisisUseCase: ObservarEntregasConEstadoAnalisisUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeCalidadUiState())
    val uiState: StateFlow<HomeCalidadUiState> = _uiState.asStateFlow()

    private val _effect = Channel<HomeCalidadEffect>(Channel.BUFFERED)
    val effect: Flow<HomeCalidadEffect> = _effect.receiveAsFlow()

    init {
        combine(observarEntregasConEstadoAnalisisUseCase(), observarCatalogosUseCase.proveedores()) { entregas, proveedores ->
            val nombrePorId = proveedores.associate { it.id to it.nombre }
            entregas.sortedByDescending { it.referencia.fechaHora }.map { it.aItemUi(nombrePorId) }
        }.onEach { items -> _uiState.update { it.copy(cargando = false, items = items) } }
            .launchIn(viewModelScope)

        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(evento: HomeCalidadEvent) {
        when (evento) {
            is HomeCalidadEvent.EntregaAnalizadaPresionada ->
                emitir(HomeCalidadEffect.NavegarADetalleAnalisis(evento.registroAcopioId))
            is HomeCalidadEvent.EntregaSinAnalizarPresionada ->
                emitir(HomeCalidadEffect.NavegarASeleccionarRegistro(evento.proveedorId))
            HomeCalidadEvent.AnalizarNuevaEntregaPresionado -> emitir(HomeCalidadEffect.NavegarABuscarProveedor)
        }
    }

    private fun emitir(efecto: HomeCalidadEffect) {
        viewModelScope.launch { _effect.send(efecto) }
    }

    private fun EntregaConEstadoAnalisis.aItemUi(nombrePorId: Map<String, String>): ItemEntregaCalidadUiState = ItemEntregaCalidadUiState(
        registroAcopioId = referencia.id,
        proveedorId = referencia.proveedorId,
        proveedorNombreTexto = referencia.proveedorNombre ?: referencia.proveedorId?.let { nombrePorId[it] } ?: "Proveedor desconocido",
        fechaHoraTexto = referencia.fechaHora.formateada(),
        litrosTexto = referencia.litros.aTextoConEscala(ESCALA_LITROS),
        tieneAnalisis = tieneAnalisis,
    )
}
