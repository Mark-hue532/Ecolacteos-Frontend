package com.ecolacteos.acopio.ui.screens.recepcion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import com.ecolacteos.acopio.presentation.recepcion.HistorialRecepcionesEffect
import com.ecolacteos.acopio.presentation.recepcion.HistorialRecepcionesEvent
import com.ecolacteos.acopio.presentation.recepcion.HistorialRecepcionesViewModel
import com.ecolacteos.acopio.presentation.recepcion.ItemRecepcionUiState
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.components.SelectorCatalogo
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `R-03 · Historial de recepciones` (Fase 8E, `MOBILE_SCREENS.md §9`, ONLINE + CACHE opcional). Sin
 * paginación (no existe en el backend, `CLAUDE.md §3.3`). "Ver pagos" es la puerta a `R-04` -- reusa
 * `BuscarProveedorScreen`, ver `NavGraph`.
 */
@Composable
fun HistorialRecepcionesScreen(
    onNavegarADetalle: (String) -> Unit,
    onNavegarABuscarProveedor: () -> Unit,
    viewModel: HistorialRecepcionesViewModel = koinViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is HistorialRecepcionesEffect.NavegarADetalle -> onNavegarADetalle(efecto.id)
                HistorialRecepcionesEffect.NavegarABuscarProveedor -> onNavegarABuscarProveedor()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Historial de recepciones", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Ver pagos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = { viewModel.onEvent(HistorialRecepcionesEvent.VerPagosPresionado) }),
            )
        }

        SelectorCatalogo(
            opciones = estado.unidades,
            seleccionado = estado.unidadFiltro,
            onSeleccion = { viewModel.onEvent(HistorialRecepcionesEvent.FiltroUnidadCambio(it)) },
            etiquetaTexto = { it.placa },
            etiqueta = "Filtrar por unidad (opcional)",
            modifier = Modifier.padding(top = Espaciado.m.dp),
        )

        when {
            estado.cargando -> CircularProgressIndicator(modifier = Modifier.padding(top = Espaciado.l.dp))
            estado.mensajeError != null ->
                Text(estado.mensajeError.orEmpty(), color = AcopioColores.error, modifier = Modifier.padding(top = Espaciado.l.dp))
            estado.vacio -> EstadoVacio(
                titulo = "Sin recepciones para mostrar",
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
            else -> LazyColumn(modifier = Modifier.padding(top = Espaciado.l.dp)) {
                items(estado.items) { item ->
                    FilaRecepcion(item, onClick = { viewModel.onEvent(HistorialRecepcionesEvent.ItemSeleccionado(item.id)) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilaRecepcion(item: ItemRecepcionUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Espaciado.s.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("${item.fechaTexto} -- ${item.turnoTexto}", style = MaterialTheme.typography.bodyLarge)
            Text("${item.litrosCampoTexto} L", style = MaterialTheme.typography.bodyMedium)
        }
        Text(item.estadoTexto, style = MaterialTheme.typography.labelLarge)
    }
}
