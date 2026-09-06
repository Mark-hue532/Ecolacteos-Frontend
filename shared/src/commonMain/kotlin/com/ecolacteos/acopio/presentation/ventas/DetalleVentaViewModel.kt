package com.ecolacteos.acopio.presentation.ventas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleVentaUseCase
import com.ecolacteos.acopio.domain.usecase.estadoSincronizacionDe
import com.ecolacteos.acopio.presentation.NO_DISPONIBLE
import com.ecolacteos.acopio.presentation.formateada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Escala de `precioUnitario`/`total` (`MOBILE_DATA_MAPPING.md §5.5`). */
private const val ESCALA_PRECIO = 2

data class DetalleVentaUiState(
    val cargando: Boolean = true,
    val encontrada: Boolean = true,
    val fechaTexto: String = "",
    val tipoClienteTexto: String = "",
    val tipoQuesoTexto: String = "",
    val cantidad: Int = 0,
    val precioUnitarioTexto: String = "",
    /** `null` -> se muestra "No disponible" (`§10.1` regla 3), nunca un cálculo local (`§8`). */
    val totalTexto: String? = null,
    val estadoSync: EstadoSincronizacion? = null,
)

/**
 * `V-03 · Detalle de venta` (`MOBILE_SCREENS.md §8`, ONLINE+CACHE). Recibe `uuidCliente` -- la clave
 * estable del dispositivo, no el `id` del servidor (que puede no existir todavía, `DATA-014`).
 */
class DetalleVentaViewModel(
    private val uuidCliente: String,
    private val obtenerDetalleVentaUseCase: ObtenerDetalleVentaUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalleVentaUiState())
    val uiState: StateFlow<DetalleVentaUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun onReintentarPresionado() = cargar()

    private fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true)
        viewModelScope.launch {
            val detalle = obtenerDetalleVentaUseCase(uuidCliente)
            if (detalle == null) {
                _uiState.value = DetalleVentaUiState(cargando = false, encontrada = false)
                return@launch
            }

            val tipoQuesoNombre = detalle.tipoQuesoNombre
                ?: observarCatalogosUseCase.tiposQueso().first().firstOrNull { it.id == detalle.tipoQuesoId }?.nombre

            _uiState.value = DetalleVentaUiState(
                cargando = false,
                encontrada = true,
                fechaTexto = detalle.fecha.formateada(),
                tipoClienteTexto = detalle.tipoCliente.name,
                tipoQuesoTexto = tipoQuesoNombre ?: NO_DISPONIBLE,
                cantidad = detalle.cantidad,
                precioUnitarioTexto = "S/ ${detalle.precioUnitario.aTextoConEscala(ESCALA_PRECIO)}",
                totalTexto = detalle.total?.let { "S/ ${it.aTextoConEscala(ESCALA_PRECIO)}" },
                estadoSync = estadoSincronizacionDe(detalle.syncStatus, null, null),
            )
        }
    }
}
