package com.ecolacteos.acopio.ui.screens.calidad

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
import com.ecolacteos.acopio.presentation.calidad.HomeCalidadEffect
import com.ecolacteos.acopio.presentation.calidad.HomeCalidadEvent
import com.ecolacteos.acopio.presentation.calidad.HomeCalidadViewModel
import com.ecolacteos.acopio.presentation.calidad.ItemEntregaCalidadUiState
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `C-01 · Home calidad` (Fase 8C, `MOBILE_SCREENS.md §6`, offline OK). Cada fila navega según su estado
 * (`analizada` -> `C-04`, `sin analizar` -> `C-02` para ese mismo proveedor); el botón principal es la
 * entrada genérica de `C-02` cuando no hay una entrega puntual en mente (busca el proveedor primero).
 */
@Composable
fun HomeCalidadScreen(
    onNavegarADetalleAnalisis: (String) -> Unit,
    onNavegarASeleccionarRegistro: (String) -> Unit,
    onNavegarABuscarProveedor: () -> Unit,
    viewModel: HomeCalidadViewModel = koinViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is HomeCalidadEffect.NavegarADetalleAnalisis -> onNavegarADetalleAnalisis(efecto.registroAcopioId)
                is HomeCalidadEffect.NavegarASeleccionarRegistro -> onNavegarASeleccionarRegistro(efecto.proveedorId)
                HomeCalidadEffect.NavegarABuscarProveedor -> onNavegarABuscarProveedor()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        Text("Análisis de calidad", style = MaterialTheme.typography.headlineMedium)

        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(top = Espaciado.m.dp))
        }

        BotonAccionPrincipal(
            texto = "Analizar una entrega",
            onClick = { viewModel.onEvent(HomeCalidadEvent.AnalizarNuevaEntregaPresionado) },
            modifier = Modifier.padding(top = Espaciado.m.dp),
        )

        when {
            estado.vacio -> EstadoVacio(
                titulo = "Todavía no hay entregas para mostrar",
                explicacion = "Buscá un proveedor para ver sus entregas y analizarlas.",
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
            else -> LazyColumn(modifier = Modifier.padding(top = Espaciado.l.dp)) {
                items(estado.items) { item ->
                    FilaEntregaCalidad(
                        item,
                        onClick = {
                            if (item.tieneAnalisis) {
                                viewModel.onEvent(HomeCalidadEvent.EntregaAnalizadaPresionada(item.registroAcopioId))
                            } else {
                                item.proveedorId?.let { viewModel.onEvent(HomeCalidadEvent.EntregaSinAnalizarPresionada(it)) }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilaEntregaCalidad(item: ItemEntregaCalidadUiState, onClick: () -> Unit) {
    val esClickeable = item.tieneAnalisis || item.proveedorId != null
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = esClickeable, onClick = onClick).padding(vertical = Espaciado.s.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(item.proveedorNombreTexto, style = MaterialTheme.typography.bodyLarge)
            Text("${item.litrosTexto} L -- ${item.fechaHoraTexto}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (item.tieneAnalisis) "Analizada" else "Sin analizar",
            style = MaterialTheme.typography.labelLarge,
            color = if (item.tieneAnalisis) MaterialTheme.colorScheme.primary else AcopioColores.atencion,
        )
    }
}
