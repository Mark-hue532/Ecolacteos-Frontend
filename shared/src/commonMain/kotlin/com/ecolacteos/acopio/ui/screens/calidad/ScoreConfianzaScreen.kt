package com.ecolacteos.acopio.ui.screens.calidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.calidad.ScoreConfianzaEvent
import com.ecolacteos.acopio.presentation.calidad.ScoreConfianzaViewModel
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `C-08 · Score de confianza del proveedor` (Fase 8E, `MOBILE_SCREENS.md §6`, ONLINE-ONLY). Un `404` es
 * "sin histórico" (`ResultadoScoreConfianza.SinHistorico`), estado vacío explicativo -- nunca el error
 * genérico.
 */
@Composable
fun ScoreConfianzaScreen(
    proveedorId: String,
    viewModel: ScoreConfianzaViewModel = koinViewModel(parameters = { parametersOf(proveedorId) }),
) {
    val estado by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp),
        verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp),
    ) {
        Text("Score de confianza", style = MaterialTheme.typography.headlineMedium)

        when {
            estado.cargando -> CircularProgressIndicator()
            estado.mensajeError != null -> EstadoVacio(
                titulo = "No se pudo cargar el score",
                explicacion = "Revisá tu conexión e intentá de nuevo.",
                textoAccion = "Reintentar",
                onAccion = { viewModel.onEvent(ScoreConfianzaEvent.ReintentarPresionado) },
            )
            !estado.encontrado -> EstadoVacio(
                titulo = "Este proveedor todavía no tiene histórico",
                explicacion = "El score se calcula con el tiempo, a medida que hay más entregas y análisis.",
            )
            else -> {
                Text("Score: ${estado.scoreTexto}", style = MaterialTheme.typography.displaySmall)
                Text("Calidad: ${estado.componenteCalidadTexto}", style = MaterialTheme.typography.bodyLarge)
                Text("Regularidad: ${estado.componenteRegularidadTexto}", style = MaterialTheme.typography.bodyLarge)
                Text("Anomalías: ${estado.componenteAnomaliasTexto}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
