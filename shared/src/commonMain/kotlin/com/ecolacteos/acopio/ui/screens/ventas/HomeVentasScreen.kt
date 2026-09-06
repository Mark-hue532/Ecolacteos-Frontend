package com.ecolacteos.acopio.ui.screens.ventas

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
import com.ecolacteos.acopio.presentation.ventas.HomeVentasEffect
import com.ecolacteos.acopio.presentation.ventas.HomeVentasEvent
import com.ecolacteos.acopio.presentation.ventas.HomeVentasViewModel
import com.ecolacteos.acopio.presentation.ventas.ItemVentaUiState
import com.ecolacteos.acopio.ui.components.BadgeEstadoSync
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `V-01 · Home ventas` (`MOBILE_SCREENS.md §8`). Sin paginación (`CLAUDE.md §3.3`, trampa #13): la lista
 * completa de hoy, sin scroll infinito ni "cargar más".
 */
@Composable
fun HomeVentasScreen(
    onNavegarARegistrar: () -> Unit,
    onNavegarADetalle: (String) -> Unit,
    viewModel: HomeVentasViewModel = koinViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                HomeVentasEffect.NavegarARegistrarVenta -> onNavegarARegistrar()
                is HomeVentasEffect.NavegarADetalle -> onNavegarADetalle(efecto.uuidCliente)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(bottom = Espaciado.m.dp))
        }

        Text("Ventas de hoy", style = MaterialTheme.typography.headlineMedium)

        BotonAccionPrincipal(
            texto = "Registrar venta",
            onClick = { viewModel.onEvent(HomeVentasEvent.RegistrarVentaPresionado) },
            modifier = Modifier.padding(vertical = Espaciado.m.dp),
        )

        when {
            estado.vacio -> EstadoVacio(
                titulo = "No hay ventas registradas todavía",
                explicacion = "Registrá la primera con el botón de arriba.",
            )
            else -> LazyColumn {
                items(estado.ventas) { item ->
                    FilaVenta(item, onClick = { viewModel.onEvent(HomeVentasEvent.VentaSeleccionada(item.uuidCliente)) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilaVenta(item: ItemVentaUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Espaciado.s.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("${item.tipoClienteTexto} -- ${item.fechaTexto}", style = MaterialTheme.typography.bodyLarge)
            Text("${item.cantidad} x ${item.precioUnitarioTexto} -- ${item.subtotalEstimadoTexto}", style = MaterialTheme.typography.bodyMedium)
        }
        if (item.estadoSync !is EstadoSincronizacion.Sincronizado) {
            BadgeEstadoSync(item.estadoSync)
        }
    }
}
