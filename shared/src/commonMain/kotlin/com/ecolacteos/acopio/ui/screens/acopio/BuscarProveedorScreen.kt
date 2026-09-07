package com.ecolacteos.acopio.ui.screens.acopio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import com.ecolacteos.acopio.presentation.acopio.BuscarProveedorEffect
import com.ecolacteos.acopio.presentation.acopio.BuscarProveedorEvent
import com.ecolacteos.acopio.presentation.acopio.BuscarProveedorViewModel
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/** `A-03 · Buscar proveedor` (`MOBILE_SCREENS.md §5`, OFFLINE REAL). */
@Composable
fun BuscarProveedorScreen(onNavegarARegistrar: (String) -> Unit, viewModel: BuscarProveedorViewModel = koinViewModel()) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is BuscarProveedorEffect.NavegarARegistrar -> onNavegarARegistrar(efecto.proveedorId)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp), verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp)) {
        Text("Buscar proveedor", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = estado.query,
            onValueChange = { texto -> viewModel.onEvent(BuscarProveedorEvent.QueryCambio(texto)) },
            label = { Text("Nombre del proveedor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            estado.vacioSinCatalogo -> EstadoVacio(
                titulo = "Todavía no descargaste el catálogo de proveedores",
                explicacion = "Conectate una vez para traerlo.",
            )
            estado.vacioSinCoincidencias -> EstadoVacio(titulo = "Ningún proveedor coincide")
            else -> LazyColumn {
                items(estado.resultados) { proveedor ->
                    FilaProveedor(proveedor, onClick = { viewModel.onEvent(BuscarProveedorEvent.ProveedorSeleccionado(proveedor.id)) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilaProveedor(proveedor: Proveedor, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Espaciado.s.dp)) {
        Text(proveedor.nombre, style = MaterialTheme.typography.bodyLarge)
        proveedor.zonaActualNombre?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
