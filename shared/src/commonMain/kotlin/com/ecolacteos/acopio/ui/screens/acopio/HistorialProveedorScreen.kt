package com.ecolacteos.acopio.ui.screens.acopio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.presentation.acopio.HistorialProveedorEffect
import com.ecolacteos.acopio.presentation.acopio.HistorialProveedorEvent
import com.ecolacteos.acopio.presentation.acopio.HistorialProveedorViewModel
import com.ecolacteos.acopio.presentation.acopio.ItemHistorialUiState
import com.ecolacteos.acopio.ui.components.BadgeEstadoSync
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `A-05 · Historial de entregas del proveedor` (`MOBILE_SCREENS.md §5`, ONLINE+CACHE). Sin paginación
 * (`CLAUDE.md §3.3`). Una fila sin `id` (propia, todavía no sincronizada) no navega -- no hay nada que
 * pedirle a `GET /api/registros-acopio/{id}` todavía.
 */
@Composable
fun HistorialProveedorScreen(proveedorId: String, onNavegarADetalle: (String) -> Unit, viewModel: HistorialProveedorViewModel = koinViewModel(parameters = { parametersOf(proveedorId) })) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is HistorialProveedorEffect.NavegarADetalle -> onNavegarADetalle(efecto.id)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        Text("Historial de entregas", style = MaterialTheme.typography.headlineMedium)

        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(top = Espaciado.m.dp, bottom = Espaciado.s.dp))
            Text("Puede haber entregas más recientes.", style = MaterialTheme.typography.bodyMedium)
        }

        when {
            estado.vacio -> EstadoVacio(
                titulo = "Sin entregas registradas todavía",
                modifier = Modifier.padding(top = Espaciado.m.dp),
            )
            else -> LazyColumn(modifier = Modifier.padding(top = Espaciado.m.dp)) {
                items(estado.items) { item ->
                    FilaHistorial(item, onClick = { item.id?.let { viewModel.onEvent(HistorialProveedorEvent.ItemSeleccionado(it)) } })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilaHistorial(item: ItemHistorialUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = item.id != null, onClick = onClick).padding(vertical = Espaciado.s.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(item.fechaHoraTexto, style = MaterialTheme.typography.bodyLarge)
            val litrosTexto = if (item.tieneObservacion) "${item.litrosTexto} L -- con observación" else "${item.litrosTexto} L"
            Text(litrosTexto, style = MaterialTheme.typography.bodyMedium)
        }
        val estadoSync = item.estadoSync
        if (estadoSync != null && estadoSync !is EstadoSincronizacion.Sincronizado) {
            BadgeEstadoSync(estadoSync)
        }
    }
}
