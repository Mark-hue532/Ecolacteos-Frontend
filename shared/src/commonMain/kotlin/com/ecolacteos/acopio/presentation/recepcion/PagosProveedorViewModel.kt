package com.ecolacteos.acopio.presentation.recepcion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.Pago
import com.ecolacteos.acopio.domain.usecase.ObtenerPagosDeProveedorUseCase
import com.ecolacteos.acopio.presentation.formateada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val ESCALA_MONTO = 2
private const val ESCALA_PRECIO_LITRO = 3

data class ItemPagoUiState(
    val semanaTexto: String,
    val litrosTotalesTexto: String,
    val precioLitroTexto: String,
    val totalTexto: String,
    val comprobanteGenerado: Boolean,
)

data class PagosProveedorUiState(
    val cargando: Boolean = true,
    val items: List<ItemPagoUiState> = emptyList(),
    val mensajeError: String? = null,
) {
    val vacio: Boolean get() = !cargando && items.isEmpty() && mensajeError == null
}

sealed interface PagosProveedorEvent {
    data object ReintentarPresionado : PagosProveedorEvent
}

/**
 * `R-04 · Pagos de proveedor` (Fase 8E, `MOBILE_SCREENS.md §9`) -- ONLINE-ONLY, solo lectura (el móvil no
 * genera pagos). [ItemPagoUiState.precioLitroTexto] con **3 decimales** (trampa #5) -- distinto de
 * [ItemPagoUiState.litrosTotalesTexto]/[ItemPagoUiState.totalTexto], que van con 2.
 */
class PagosProveedorViewModel(
    private val proveedorId: String,
    private val obtenerPagosDeProveedorUseCase: ObtenerPagosDeProveedorUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PagosProveedorUiState())
    val uiState: StateFlow<PagosProveedorUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun onEvent(evento: PagosProveedorEvent) {
        when (evento) {
            PagosProveedorEvent.ReintentarPresionado -> cargar()
        }
    }

    private fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true, mensajeError = null)
        viewModelScope.launch {
            when (val resultado = obtenerPagosDeProveedorUseCase(proveedorId)) {
                is ResultadoDominio.Exito -> _uiState.value =
                    PagosProveedorUiState(cargando = false, items = resultado.datos.map { it.aItemUi() })
                is ResultadoDominio.Error -> _uiState.value = PagosProveedorUiState(cargando = false, mensajeError = resultado.error.mensaje)
            }
        }
    }

    private fun Pago.aItemUi(): ItemPagoUiState = ItemPagoUiState(
        semanaTexto = "${semanaInicio.formateada()} -- ${semanaFin.formateada()}",
        litrosTotalesTexto = litrosTotales.aTextoConEscala(ESCALA_MONTO),
        precioLitroTexto = precioLitro.aTextoConEscala(ESCALA_PRECIO_LITRO),
        totalTexto = total.aTextoConEscala(ESCALA_MONTO),
        comprobanteGenerado = comprobanteGenerado,
    )
}
