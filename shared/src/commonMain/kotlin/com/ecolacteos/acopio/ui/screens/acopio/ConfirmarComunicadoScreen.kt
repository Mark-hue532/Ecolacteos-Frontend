package com.ecolacteos.acopio.ui.screens.acopio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.presentation.acopio.ConfirmarComunicadoEffect
import com.ecolacteos.acopio.presentation.acopio.ConfirmarComunicadoEvent
import com.ecolacteos.acopio.presentation.acopio.ConfirmarComunicadoViewModel
import com.ecolacteos.acopio.ui.components.BloqueoOnlineOnly
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `A-07 · Confirmar comunicado a proveedor` (`MOBILE_SCREENS.md §5`, ONLINE-ONLY). Sin conexión: pantalla
 * completa bloqueada con `BloqueoOnlineOnly` -- no hay nada útil que hacer sin señal, la acción entera
 * requiere el `POST` (`DATA-005`/`§18.2`, trampa #9: nunca se encola).
 */
@Composable
fun ConfirmarComunicadoScreen(
    comunicadoId: String,
    onConfirmadoConExito: () -> Unit,
    viewModel: ConfirmarComunicadoViewModel = koinViewModel(parameters = { parametersOf(comunicadoId) }),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                ConfirmarComunicadoEffect.ConfirmadoConExito -> onConfirmadoConExito()
            }
        }
    }

    if (!estado.hayConexion) {
        BloqueoOnlineOnly(mensaje = "Requiere conexión para confirmar el comunicado.")
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp), verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp)) {
        Text("Confirmar comunicado", style = MaterialTheme.typography.headlineMedium)
        estado.mensajeComunicado?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }

        OutlinedTextField(
            value = estado.query,
            onValueChange = { viewModel.onEvent(ConfirmarComunicadoEvent.QueryCambio(it)) },
            label = { Text("Buscar proveedor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        estado.proveedorSeleccionado?.let { seleccionado ->
            Text("Proveedor: ${seleccionado.nombre}", style = MaterialTheme.typography.titleLarge)
        } ?: LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(estado.resultados) { proveedor ->
                FilaProveedor(proveedor, onClick = { viewModel.onEvent(ConfirmarComunicadoEvent.ProveedorSeleccionado(proveedor)) })
            }
        }

        estado.error?.let { Text(it, color = AcopioColores.error, style = MaterialTheme.typography.bodyMedium) }

        BotonAccionPrincipal(
            texto = if (estado.confirmando) "Confirmando..." else "Confirmar",
            onClick = { viewModel.onEvent(ConfirmarComunicadoEvent.ConfirmarPresionado) },
            habilitado = estado.puedeConfirmar,
        )
    }
}

@Composable
private fun FilaProveedor(proveedor: Proveedor, onClick: () -> Unit) {
    Text(
        proveedor.nombre,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Espaciado.s.dp),
    )
}
