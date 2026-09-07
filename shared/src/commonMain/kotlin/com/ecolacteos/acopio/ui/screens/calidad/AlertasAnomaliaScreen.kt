package com.ecolacteos.acopio.ui.screens.calidad

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
import com.ecolacteos.acopio.presentation.calidad.AlertaAnomaliaUiState
import com.ecolacteos.acopio.presentation.calidad.AlertasAnomaliaEvent
import com.ecolacteos.acopio.presentation.calidad.AlertasAnomaliaViewModel
import com.ecolacteos.acopio.presentation.NO_DISPONIBLE
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.components.SelectorCatalogo
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `C-07 · Alertas de anomalías` (Fase 8E, `MOBILE_SCREENS.md §6`, ONLINE-ONLY). `zonaId` es obligatorio --
 * el selector sale de `proveedor_cache` (decisión 1 del checkpoint de 8E), nunca se consulta sin elegir
 * zona antes.
 */
@Composable
fun AlertasAnomaliaScreen(viewModel: AlertasAnomaliaViewModel = koinViewModel()) {
    val estado by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        Text("Alertas de anomalías", style = MaterialTheme.typography.headlineMedium)

        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(top = Espaciado.m.dp))
        }

        SelectorCatalogo(
            opciones = estado.zonas,
            seleccionado = estado.zonaSeleccionada,
            onSeleccion = { viewModel.onEvent(AlertasAnomaliaEvent.ZonaCambio(it)) },
            etiquetaTexto = { it.nombre },
            etiqueta = "Zona",
            modifier = Modifier.padding(top = Espaciado.m.dp),
        )

        when {
            estado.cargandoZonas || estado.consultando -> CircularProgressIndicator(modifier = Modifier.padding(top = Espaciado.l.dp))
            estado.mensajeError != null ->
                Text(estado.mensajeError.orEmpty(), color = AcopioColores.error, modifier = Modifier.padding(top = Espaciado.l.dp))
            estado.vacio -> EstadoVacio(
                titulo = "Sin alertas para esta zona",
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
            else -> LazyColumn(modifier = Modifier.padding(top = Espaciado.l.dp)) {
                items(estado.alertas) { alerta ->
                    FilaAlerta(alerta)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilaAlerta(alerta: AlertaAnomaliaUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Espaciado.s.dp)) {
        Text(alerta.proveedorNombre, style = MaterialTheme.typography.bodyLarge)
        Text("${alerta.tipoTexto} -- ${alerta.severidadTexto}", style = MaterialTheme.typography.bodyMedium)
        Text("z-score: ${alerta.zScoreTexto ?: NO_DISPONIBLE} -- ${alerta.creadoTexto}", style = MaterialTheme.typography.bodySmall)
    }
}
