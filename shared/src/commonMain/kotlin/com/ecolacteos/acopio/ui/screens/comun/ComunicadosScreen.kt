package com.ecolacteos.acopio.ui.screens.comun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.comun.ComunicadosEffect
import com.ecolacteos.acopio.presentation.comun.ComunicadosEvent
import com.ecolacteos.acopio.presentation.comun.ComunicadosViewModel
import com.ecolacteos.acopio.presentation.comun.ItemComunicadoUiState
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/** `S-06 · Comunicados` (`MOBILE_SCREENS.md §4`, READ-CACHE). */
@Composable
fun ComunicadosScreen(onNavegarAConfirmar: (String) -> Unit, viewModel: ComunicadosViewModel = koinViewModel()) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is ComunicadosEffect.NavegarAConfirmar -> onNavegarAConfirmar(efecto.comunicadoId)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        Text("Comunicados", style = MaterialTheme.typography.headlineMedium)

        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(top = Espaciado.m.dp))
        }

        when {
            estado.vacio -> EstadoVacio(titulo = "No hay comunicados", modifier = Modifier.padding(top = Espaciado.l.dp))
            else -> LazyColumn(modifier = Modifier.padding(top = Espaciado.m.dp)) {
                items(estado.items) { fila ->
                    FilaComunicado(fila, onClick = { viewModel.onEvent(ComunicadosEvent.ConfirmarPresionado(fila.id)) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilaComunicado(item: ItemComunicadoUiState, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Espaciado.s.dp)) {
        Text(item.mensaje, style = MaterialTheme.typography.bodyLarge)
        Text("${item.fechaTexto} — ${item.zonasTexto}", style = MaterialTheme.typography.bodyMedium)
        if (item.confirmado) {
            Text("Confirmado a un proveedor", color = AcopioColores.exito, style = MaterialTheme.typography.labelLarge)
        }
    }
}
