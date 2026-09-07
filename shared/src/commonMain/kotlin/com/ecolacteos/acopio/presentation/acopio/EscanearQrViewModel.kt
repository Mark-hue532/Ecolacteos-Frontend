package com.ecolacteos.acopio.presentation.acopio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.data.repository.ResultadoResolucionQr
import com.ecolacteos.acopio.domain.usecase.ResolverProveedorPorQrUseCase
import com.ecolacteos.acopio.plataforma.EstadoPermiso
import com.ecolacteos.acopio.plataforma.GestorPermisos
import com.ecolacteos.acopio.plataforma.Permiso
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EscanearQrUiState(
    val estadoPermiso: EstadoPermiso = EstadoPermiso.NO_DETERMINADO,
    val mensaje: String? = null,
    val resolviendo: Boolean = false,
) {
    val puedeEscanear: Boolean get() = estadoPermiso == EstadoPermiso.CONCEDIDO
}

sealed interface EscanearQrEvent {
    data object PermitirCamaraPresionado : EscanearQrEvent
    data class PermisoResuelto(val estado: EstadoPermiso) : EscanearQrEvent
    data object IrAAjustesPresionado : EscanearQrEvent
    data class CodigoDetectado(val codigo: String) : EscanearQrEvent
    data object BuscarPorNombrePresionado : EscanearQrEvent
}

sealed interface EscanearQrEffect {
    data object SolicitarPermiso : EscanearQrEffect
    data class NavegarARegistrar(val proveedorId: String) : EscanearQrEffect
    data object NavegarABuscar : EscanearQrEffect
}

/**
 * `A-02 · Escanear QR de proveedor` (`MOBILE_SCREENS.md §5`, OFFLINE REAL). El flujo completo
 * (SQLite → red si hace falta → mensaje/navegación) vive acá, no en el `@Composable` -- trampa #15 de
 * `PROMPT_FASE_08A.md`. `EscanerQr` (`plataforma/EscanerQr.kt`) solo reporta texto crudo vía
 * [EscanearQrEvent.CodigoDetectado].
 */
class EscanearQrViewModel(
    private val resolverProveedorPorQrUseCase: ResolverProveedorPorQrUseCase,
    private val gestorPermisos: GestorPermisos,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EscanearQrUiState(
            estadoPermiso = if (gestorPermisos.tieneConcedido(Permiso.CAMARA)) EstadoPermiso.CONCEDIDO else EstadoPermiso.NO_DETERMINADO,
        ),
    )
    val uiState: StateFlow<EscanearQrUiState> = _uiState.asStateFlow()

    private val _effect = Channel<EscanearQrEffect>(Channel.BUFFERED)
    val effect: Flow<EscanearQrEffect> = _effect.receiveAsFlow()

    fun onEvent(evento: EscanearQrEvent) {
        when (evento) {
            EscanearQrEvent.PermitirCamaraPresionado ->
                viewModelScope.launch { _effect.send(EscanearQrEffect.SolicitarPermiso) }
            is EscanearQrEvent.PermisoResuelto ->
                _uiState.update { it.copy(estadoPermiso = evento.estado) }
            EscanearQrEvent.IrAAjustesPresionado -> gestorPermisos.abrirAjustesDeLaApp()
            is EscanearQrEvent.CodigoDetectado -> resolverCodigo(evento.codigo)
            EscanearQrEvent.BuscarPorNombrePresionado ->
                viewModelScope.launch { _effect.send(EscanearQrEffect.NavegarABuscar) }
        }
    }

    /**
     * La cámara reporta el mismo código repetidas veces mientras siga en cuadro -- `resolviendo` evita
     * disparar `ResolverProveedorPorQrUseCase` (y por lo tanto la llamada de red del fallback) más de una
     * vez por escaneo.
     */
    private fun resolverCodigo(codigo: String) {
        if (_uiState.value.resolviendo) return
        _uiState.update { it.copy(resolviendo = true, mensaje = null) }

        viewModelScope.launch {
            when (val resultado = resolverProveedorPorQrUseCase(codigo)) {
                is ResultadoResolucionQr.Encontrado -> {
                    _uiState.update { it.copy(resolviendo = false) }
                    _effect.send(EscanearQrEffect.NavegarARegistrar(resultado.proveedor.id))
                }
                ResultadoResolucionQr.NoEncontrado -> _uiState.update {
                    it.copy(resolviendo = false, mensaje = "Este código no corresponde a ningún proveedor")
                }
                ResultadoResolucionQr.SinSenalParaConsultar -> {
                    _uiState.update {
                        it.copy(
                            resolviendo = false,
                            mensaje = "No reconocemos este código y no hay señal para consultarlo. Podés buscar al proveedor por nombre.",
                        )
                    }
                    _effect.send(EscanearQrEffect.NavegarABuscar)
                }
            }
        }
    }
}
