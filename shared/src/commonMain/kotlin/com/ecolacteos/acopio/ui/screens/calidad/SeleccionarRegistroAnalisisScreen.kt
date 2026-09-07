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
import com.ecolacteos.acopio.presentation.calidad.ItemSeleccionRegistroUiState
import com.ecolacteos.acopio.presentation.calidad.SeleccionarRegistroEffect
import com.ecolacteos.acopio.presentation.calidad.SeleccionarRegistroEvent
import com.ecolacteos.acopio.presentation.calidad.SeleccionarRegistroAnalisisViewModel
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `C-02 · Seleccionar registro de acopio a analizar ★` (Fase 8C, `MOBILE_SCREENS.md §6`). El aviso de
 * retención (`DATA-003`) va en la fila misma -- se ve **antes** de tocarla, nunca después de guardar
 * `C-03`. "Actualizar" siempre visible con conexión (decisión 1 del checkpoint).
 */
@Composable
fun SeleccionarRegistroAnalisisScreen(
    proveedorId: String,
    onNavegarACapturar: (uuidCliente: String?, serverId: String?) -> Unit,
    viewModel: SeleccionarRegistroAnalisisViewModel = koinViewModel(parameters = { parametersOf(proveedorId) }),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is SeleccionarRegistroEffect.NavegarACapturar ->
                    onNavegarACapturar(efecto.registroAcopioUuidCliente, efecto.registroAcopioServerId)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Elegí la entrega a analizar", style = MaterialTheme.typography.headlineMedium)
            if (estado.hayConexion) {
                Text(
                    if (estado.actualizando) "Actualizando..." else "Actualizar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = !estado.actualizando) {
                        viewModel.onEvent(SeleccionarRegistroEvent.ActualizarPresionado)
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
            else -> LazyColumn(modifier = Modifier.padding(top = Espaciado.m.dp)) {
                items(estado.items) { item ->
                    FilaSeleccionRegistro(item, onClick = { viewModel.onEvent(SeleccionarRegistroEvent.ItemSeleccionado(item.idUi)) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilaSeleccionRegistro(item: ItemSeleccionRegistroUiState, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Espaciado.s.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(item.fechaHoraTexto, style = MaterialTheme.typography.bodyLarge)
            Text("${item.litrosTexto} L", style = MaterialTheme.typography.bodyLarge)
        }
        item.aviso?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AcopioColores.atencion, modifier = Modifier.padding(top = Espaciado.s.dp))
        }
    }
}
