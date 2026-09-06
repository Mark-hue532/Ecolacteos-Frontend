package com.ecolacteos.acopio.ui.screens.ventas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.NO_DISPONIBLE
import com.ecolacteos.acopio.presentation.ventas.DetalleVentaViewModel
import com.ecolacteos.acopio.ui.components.BadgeEstadoSync
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `V-03 · Detalle de venta` (`MOBILE_SCREENS.md §8`, ONLINE+CACHE). Muestra el `total` tal cual lo devolvió
 * el servidor -- nunca recalculado (`§8`). `null` -> "No disponible" (`§10.1` regla 3), nunca "0".
 */
@Composable
fun DetalleVentaScreen(uuidCliente: String, viewModel: DetalleVentaViewModel = koinViewModel(parameters = { parametersOf(uuidCliente) })) {
    val estado by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp),
        verticalArrangement = Arrangement.spacedBy(Espaciado.s.dp),
    ) {
        Text("Detalle de venta", style = MaterialTheme.typography.headlineMedium)

        when {
            estado.cargando -> CircularProgressIndicator()
            !estado.encontrada -> EstadoVacio(titulo = "No se encontró la venta")
            else -> {
                Text("Fecha: ${estado.fechaTexto}")
                Text("Tipo de cliente: ${estado.tipoClienteTexto}")
                Text("Tipo de queso: ${estado.tipoQuesoTexto}")
                Text("Cantidad: ${estado.cantidad}")
                Text("Precio unitario: ${estado.precioUnitarioTexto}")
                Text("Total: ${estado.totalTexto ?: NO_DISPONIBLE}", style = MaterialTheme.typography.titleLarge)
                estado.estadoSync?.let { BadgeEstadoSync(it) }
            }
        }
    }
}
