package com.ecolacteos.acopio.ui.screens.produccion

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
import com.ecolacteos.acopio.presentation.produccion.HomeProduccionEffect
import com.ecolacteos.acopio.presentation.produccion.HomeProduccionEvent
import com.ecolacteos.acopio.presentation.produccion.HomeProduccionViewModel
import com.ecolacteos.acopio.presentation.produccion.ItemLoteUiState
import com.ecolacteos.acopio.ui.components.BadgeEstadoSync
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/** `P-01 · Home producción` (Fase 8D, `MOBILE_SCREENS.md §7`, offline OK). */
@Composable
fun HomeProduccionScreen(
    onNavegarADetalleLote: (String) -> Unit,
    onNavegarABuscarProveedor: () -> Unit,
    viewModel: HomeProduccionViewModel = koinViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is HomeProduccionEffect.NavegarADetalleLote -> onNavegarADetalleLote(efecto.serverId)
                HomeProduccionEffect.NavegarABuscarProveedor -> onNavegarABuscarProveedor()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        Text("Lotes de producción", style = MaterialTheme.typography.headlineMedium)

        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(top = Espaciado.m.dp))
        }

        BotonAccionPrincipal(
            texto = "Registrar lote",
            onClick = { viewModel.onEvent(HomeProduccionEvent.RegistrarLotePresionado) },
            modifier = Modifier.padding(top = Espaciado.m.dp),
        )

        when {
            estado.vacio -> EstadoVacio(
                titulo = "Todavía no registraste ningún lote",
                explicacion = "Elegí las entregas de un proveedor y registrá tu primer lote.",
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
            else -> LazyColumn(modifier = Modifier.padding(top = Espaciado.l.dp)) {
                items(estado.items) { item ->
                    FilaLote(
                        item,
                        onClick = { item.serverId?.let { viewModel.onEvent(HomeProduccionEvent.LoteSeleccionado(it)) } },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilaLote(item: ItemLoteUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = item.serverId != null, onClick = onClick).padding(vertical = Espaciado.s.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(item.tipoQuesoNombreTexto, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${item.litrosUsadosTexto} L -- ${item.unidadesObtenidas} u -- ${item.fechaTexto}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Esperado: ${item.rendimientoEsperadoTexto} %", style = MaterialTheme.typography.bodyMedium)
        }
        BadgeEstadoSync(item.estadoSync)
    }
}
