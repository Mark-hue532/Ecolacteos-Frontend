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
import com.ecolacteos.acopio.presentation.acopio.ItemRutaUiState
import com.ecolacteos.acopio.presentation.acopio.RutaDelDiaEffect
import com.ecolacteos.acopio.presentation.acopio.RutaDelDiaEvent
import com.ecolacteos.acopio.presentation.acopio.RutaDelDiaViewModel
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `A-01 · Ruta del día` (`MOBILE_SCREENS.md §5`, READ-CACHE). Tocar una fila navega directo a `A-04`
 * (decisión del checkpoint: la ruta ya identifica al proveedor, no hace falta pasar por `A-02`/`A-03`).
 * `A-04` expone "Ver historial" cuando hace falta, así esta pantalla no duplica ese acceso por fila.
 */
@Composable
fun RutaDelDiaScreen(
    onNavegarAEscanear: () -> Unit,
    onNavegarABuscar: () -> Unit,
    onNavegarARegistrar: (String) -> Unit,
    viewModel: RutaDelDiaViewModel = koinViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                RutaDelDiaEffect.NavegarAEscanearQr -> onNavegarAEscanear()
                RutaDelDiaEffect.NavegarABuscarProveedor -> onNavegarABuscar()
                is RutaDelDiaEffect.NavegarARegistrar -> onNavegarARegistrar(efecto.proveedorId)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(bottom = Espaciado.m.dp))
        }

        Text("Tu ruta de hoy", style = MaterialTheme.typography.headlineMedium)

        BotonAccionPrincipal(
            texto = "Escanear QR",
            onClick = { viewModel.onEvent(RutaDelDiaEvent.EscanearQrPresionado) },
            modifier = Modifier.padding(vertical = Espaciado.m.dp),
        )

        when {
            !estado.zonaDeterminada -> EstadoVacio(
                titulo = "No pudimos determinar tu zona todavía",
                explicacion = "Necesitás tener al menos una unidad asignada. Contactá a tu administrador si el problema sigue.",
            )
            estado.vacioNuncaDescargada -> EstadoVacio(
                titulo = "Todavía no descargaste tu ruta",
                explicacion = "Conectate una vez para traerla.",
            )
            estado.vacioSinRuta -> EstadoVacio(
                titulo = "No hay ruta definida para tu zona",
                explicacion = "La define tu administrador desde el panel web.",
            )
            else -> LazyColumn {
                items(estado.items) { item ->
                    FilaRuta(item, onClick = { viewModel.onEvent(RutaDelDiaEvent.ProveedorSeleccionado(item.proveedorId)) })
                    HorizontalDivider()
                }
            }
        }

        Text(
            "¿No está en tu ruta? Buscar proveedor",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Espaciado.m.dp)
                .clickable { viewModel.onEvent(RutaDelDiaEvent.BuscarProveedorPresionado) },
        )
    }
}

@Composable
private fun FilaRuta(item: ItemRutaUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Espaciado.s.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("${item.orden}. ${item.proveedorNombre}", style = MaterialTheme.typography.bodyLarge)
            item.horaEstimadaTexto?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        if (item.visitadoHoy) {
            Text("Visitado", color = AcopioColores.exito, style = MaterialTheme.typography.labelLarge)
        }
    }
}
