package com.ecolacteos.acopio.ui.screens.recepcion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.recepcion.ItemPagoUiState
import com.ecolacteos.acopio.presentation.recepcion.PagosProveedorViewModel
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `R-04 · Pagos de proveedor` (Fase 8E, `MOBILE_SCREENS.md §9`, ONLINE-ONLY, solo lectura). El móvil no
 * genera pagos -- no hay acción de escritura en esta pantalla.
 */
@Composable
fun PagosProveedorScreen(
    proveedorId: String,
    viewModel: PagosProveedorViewModel = koinViewModel(parameters = { parametersOf(proveedorId) }),
) {
    val estado by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        Text("Pagos del proveedor", style = MaterialTheme.typography.headlineMedium)

        when {
            estado.cargando -> CircularProgressIndicator(modifier = Modifier.padding(top = Espaciado.l.dp))
            estado.mensajeError != null ->
                Text(estado.mensajeError.orEmpty(), color = AcopioColores.error, modifier = Modifier.padding(top = Espaciado.l.dp))
            estado.vacio -> EstadoVacio(
                titulo = "Sin pagos para mostrar",
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
            else -> LazyColumn(modifier = Modifier.padding(top = Espaciado.l.dp)) {
                items(estado.items) { item ->
                    FilaPago(item)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilaPago(item: ItemPagoUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Espaciado.s.dp)) {
        Text(item.semanaTexto, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${item.litrosTotalesTexto} L -- $${item.precioLitroTexto}/L -- total $${item.totalTexto}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(if (item.comprobanteGenerado) "Comprobante generado" else "Sin comprobante", style = MaterialTheme.typography.labelLarge)
    }
}
