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
import androidx.compose.material3.Checkbox
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
import com.ecolacteos.acopio.presentation.produccion.ItemSeleccionLoteUiState
import com.ecolacteos.acopio.presentation.produccion.SeleccionarRegistrosLoteEffect
import com.ecolacteos.acopio.presentation.produccion.SeleccionarRegistrosLoteEvent
import com.ecolacteos.acopio.presentation.produccion.SeleccionarRegistrosLoteViewModel
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `P-02 · Seleccionar registros de acopio para el lote ★` (Fase 8D, `MOBILE_SCREENS.md §7`). Selección
 * múltiple sobre las mismas tres categorías de `C-02` -- el aviso de retención agregada (`§4.1`) va debajo
 * de la lista, visible **antes** de continuar a `P-03` (`DATA-003`).
 */
@Composable
fun SeleccionarRegistrosLoteScreen(
    proveedorId: String,
    onNavegarACapturar: (uuidClientes: List<String>, serverIds: List<String>, totalLitrosTexto: String) -> Unit,
    viewModel: SeleccionarRegistrosLoteViewModel = koinViewModel(parameters = { parametersOf(proveedorId) }),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is SeleccionarRegistrosLoteEffect.NavegarACapturar ->
                    onNavegarACapturar(efecto.registroAcopioUuidClientes, efecto.registroAcopioServerIds, efecto.totalLitrosTexto)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Elegí las entregas del lote", style = MaterialTheme.typography.headlineMedium)
            if (estado.hayConexion) {
                Text(
                    if (estado.actualizando) "Actualizando..." else "Actualizar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = !estado.actualizando) {
                        viewModel.onEvent(SeleccionarRegistrosLoteEvent.ActualizarPresionado)
                    },
                )
            }
        }

        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(top = Espaciado.m.dp))
        }

        when {
            estado.vacio -> EstadoVacio(
                titulo = "No encontramos entregas de este proveedor",
                explicacion = "Si la entrega que buscás se registró recién en otro dispositivo, va a aparecer cuando ambos tengan señal.",
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
            else -> {
                LazyColumn(modifier = Modifier.weight(1f, fill = false).padding(top = Espaciado.m.dp)) {
                    items(estado.items) { item ->
                        FilaSeleccionLote(item, onToggle = { viewModel.onEvent(SeleccionarRegistrosLoteEvent.ItemToggled(item.idUi)) })
                        HorizontalDivider()
                    }
                }

                Text(
                    "Total seleccionado: ${estado.totalLitrosSeleccionadoTexto} L",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Espaciado.m.dp),
                )
                estado.avisoRetencion?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = AcopioColores.atencion, modifier = Modifier.padding(top = Espaciado.s.dp))
                }

                BotonAccionPrincipal(
                    texto = "Continuar",
                    onClick = { viewModel.onEvent(SeleccionarRegistrosLoteEvent.ContinuarPresionado) },
                    habilitado = estado.puedeContinuar,
                    modifier = Modifier.padding(top = Espaciado.m.dp),
                )
            }
        }
    }
}

@Composable
private fun FilaSeleccionLote(item: ItemSeleccionLoteUiState, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = Espaciado.s.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(item.fechaHoraTexto, style = MaterialTheme.typography.bodyLarge)
            Text("${item.litrosTexto} L", style = MaterialTheme.typography.bodyMedium)
        }
        Checkbox(checked = item.seleccionado, onCheckedChange = { onToggle() })
    }
}
