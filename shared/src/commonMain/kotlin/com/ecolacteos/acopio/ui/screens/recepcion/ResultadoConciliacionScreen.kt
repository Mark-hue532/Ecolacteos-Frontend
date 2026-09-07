package com.ecolacteos.acopio.ui.screens.recepcion

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
import com.ecolacteos.acopio.presentation.recepcion.ResultadoConciliacionEvent
import com.ecolacteos.acopio.presentation.recepcion.ResultadoConciliacionViewModel
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `R-02 · Resultado de conciliación` (Fase 8E, `MOBILE_SCREENS.md §9`, ONLINE-ONLY, sin cache). Los tres
 * campos calculados son de solo lectura; `litrosRegistradosAcopio` nulo se muestra como texto explicativo,
 * nunca `"0.00"` (`§10.1` regla 3).
 */
@Composable
fun ResultadoConciliacionScreen(
    id: String,
    viewModel: ResultadoConciliacionViewModel = koinViewModel(parameters = { parametersOf(id) }),
) {
    val estado by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp),
        verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp),
    ) {
        Text("Resultado de conciliación", style = MaterialTheme.typography.headlineMedium)

        when {
            estado.cargando -> CircularProgressIndicator()
            !estado.encontrado -> EstadoVacio(
                titulo = "No se pudo cargar el resultado",
                explicacion = "Revisá tu conexión e intentá de nuevo.",
                textoAccion = "Reintentar",
                onAccion = { viewModel.onEvent(ResultadoConciliacionEvent.ReintentarPresionado) },
            )
            else -> {
                Text("Fecha: ${estado.fechaTexto} -- Turno: ${estado.turnoTexto}", style = MaterialTheme.typography.bodyLarge)
                Text("Litros de campo: ${estado.litrosCampoTexto}", style = MaterialTheme.typography.bodyLarge)
                Text("Litros de planta: ${estado.litrosPlantaTexto}", style = MaterialTheme.typography.bodyLarge)
                Text("Diferencia: ${estado.diferenciaPctTexto} %", style = MaterialTheme.typography.bodyLarge)
                Text("Estado: ${estado.estadoTexto}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Litros registrados en acopio: ${estado.litrosRegistradosAcopioTexto ?: "Sin registros de acopio para esta unidad y fecha"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
