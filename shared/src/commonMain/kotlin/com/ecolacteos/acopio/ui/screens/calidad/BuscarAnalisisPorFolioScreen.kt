package com.ecolacteos.acopio.ui.screens.calidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.NO_DISPONIBLE
import com.ecolacteos.acopio.presentation.calidad.BuscarAnalisisPorFolioEvent
import com.ecolacteos.acopio.presentation.calidad.BuscarAnalisisPorFolioViewModel
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.CampoDecimal
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `C-05 · Buscar análisis por folio` (Fase 8E, `MOBILE_SCREENS.md §6`, ONLINE-ONLY). Sin conexión, el campo
 * de búsqueda queda deshabilitado (`estado.puedeBuscar`), no solo el botón -- no hay nada que reintentar
 * localmente. Sin resultados es un estado vacío explicativo, nunca el mapeo genérico de error.
 */
@Composable
fun BuscarAnalisisPorFolioScreen(viewModel: BuscarAnalisisPorFolioViewModel = koinViewModel()) {
    val estado by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp),
        verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp),
    ) {
        Text("Buscar análisis por folio", style = MaterialTheme.typography.headlineMedium)

        if (!estado.hayConexion) {
            BannerSinConexion()
        }

        CampoDecimal(
            valor = estado.folioTexto,
            onValorCambia = { viewModel.onEvent(BuscarAnalisisPorFolioEvent.FolioCambio(it)) },
            etiqueta = "Folio de la muestra",
            habilitado = estado.hayConexion,
        )

        BotonAccionPrincipal(
            texto = if (estado.buscando) "Buscando..." else "Buscar",
            onClick = { viewModel.onEvent(BuscarAnalisisPorFolioEvent.BuscarPresionado) },
            habilitado = estado.puedeBuscar,
        )

        estado.mensajeError?.let { Text(it, color = AcopioColores.error, style = MaterialTheme.typography.bodyMedium) }

        if (estado.sinResultados) {
            EstadoVacio(
                titulo = "Ningún análisis con ese folio",
                explicacion = "Revisá que el folio esté bien escrito.",
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
        }

        estado.resultado?.let { resultado ->
            Column(modifier = Modifier.fillMaxWidth().padding(top = Espaciado.l.dp), verticalArrangement = Arrangement.spacedBy(Espaciado.s.dp)) {
                Text("Folio: ${resultado.folioMuestraTexto}", style = MaterialTheme.typography.titleLarge)
                resultado.aguaTexto?.let { Text("Agua: $it %", style = MaterialTheme.typography.bodyMedium) }
                resultado.proteinaTexto?.let { Text("Proteína: $it %", style = MaterialTheme.typography.bodyMedium) }
                resultado.lactosaTexto?.let { Text("Lactosa: $it %", style = MaterialTheme.typography.bodyMedium) }
                resultado.densidadTexto?.let { Text("Densidad: $it", style = MaterialTheme.typography.bodyMedium) }
                resultado.temperaturaTexto?.let { Text("Temperatura: $it °C", style = MaterialTheme.typography.bodyMedium) }
                resultado.phTexto?.let { Text("pH: $it", style = MaterialTheme.typography.bodyMedium) }
                Text("Agua añadida: ${resultado.aguaAnadidaTexto}", style = MaterialTheme.typography.bodyMedium)
                Text("Resultado: ${resultado.resultadoTexto ?: NO_DISPONIBLE}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
