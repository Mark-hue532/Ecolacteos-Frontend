package com.ecolacteos.acopio.ui.screens.produccion

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
import com.ecolacteos.acopio.presentation.NO_DISPONIBLE
import com.ecolacteos.acopio.presentation.produccion.DetalleLoteEvent
import com.ecolacteos.acopio.presentation.produccion.DetalleLoteViewModel
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `P-04 · Detalle de lote` (Fase 8D, `MOBILE_SCREENS.md §7`, ONLINE+CACHE). Si `rendimientoPct` es nulo no
 * se dibuja ninguna comparación (`§6`, trampa #7) -- solo "No calculado".
 */
@Composable
fun DetalleLoteScreen(id: String, viewModel: DetalleLoteViewModel = koinViewModel(parameters = { parametersOf(id) })) {
    val estado by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp),
        verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp),
    ) {
        Text("Detalle del lote", style = MaterialTheme.typography.headlineMedium)

        when {
            estado.cargando -> CircularProgressIndicator()
            !estado.encontrado -> EstadoVacio(
                titulo = "No se pudo cargar el detalle",
                explicacion = "Revisá tu conexión e intentá de nuevo.",
                textoAccion = "Reintentar",
                onAccion = { viewModel.onEvent(DetalleLoteEvent.ReintentarPresionado) },
            )
            else -> {
                Text(estado.tipoQuesoNombreTexto, style = MaterialTheme.typography.titleLarge)
                Text("Fecha: ${estado.fechaTexto}", style = MaterialTheme.typography.bodyMedium)
                Text("${estado.litrosUsadosTexto} L usados -- ${estado.unidadesObtenidas} unidades obtenidas", style = MaterialTheme.typography.bodyLarge)

                Text("Rendimiento esperado: ${estado.rendimientoEsperadoTexto} %", style = MaterialTheme.typography.bodyMedium)

                if (estado.hayComparacion) {
                    Text("Rendimiento real: ${estado.rendimientoRealTexto} %", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("Rendimiento real: $NO_DISPONIBLE -- el servidor todavía no lo calculó", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
