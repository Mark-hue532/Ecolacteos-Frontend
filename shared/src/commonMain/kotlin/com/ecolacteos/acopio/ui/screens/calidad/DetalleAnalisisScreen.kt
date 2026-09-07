package com.ecolacteos.acopio.ui.screens.calidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.calidad.DetalleAnalisisEffect
import com.ecolacteos.acopio.presentation.calidad.DetalleAnalisisEvent
import com.ecolacteos.acopio.presentation.calidad.DetalleAnalisisCalidadViewModel
import com.ecolacteos.acopio.presentation.NO_DISPONIBLE
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.components.FechaEtiquetada
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `C-04 · Detalle de análisis` (Fase 8C, `MOBILE_SCREENS.md §6`, ONLINE+CACHE). Los 6 parámetros nulos se
 * omiten -- ninguno se muestra como `0`. `resultado` es un enum abierto (`§1.6`); `null` significa que el
 * servidor todavía no lo confirmó (degradado a la propia captura, ver `DetalleAnalisisCalidadViewModel`).
 */
@Composable
fun DetalleAnalisisScreen(
    registroAcopioId: String,
    onNavegarARegistrarCorreccion: (String) -> Unit,
    viewModel: DetalleAnalisisCalidadViewModel = koinViewModel(parameters = { parametersOf(registroAcopioId) }),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is DetalleAnalisisEffect.NavegarARegistrarCorreccion -> onNavegarARegistrarCorreccion(efecto.registroAcopioId)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp),
        verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp),
    ) {
        Text("Detalle del análisis", style = MaterialTheme.typography.headlineMedium)

        when {
            estado.cargando -> CircularProgressIndicator()
            !estado.encontrado -> EstadoVacio(
                titulo = "No se pudo cargar el detalle",
                explicacion = "Revisá tu conexión e intentá de nuevo.",
                textoAccion = "Reintentar",
                onAccion = { viewModel.onEvent(DetalleAnalisisEvent.ReintentarPresionado) },
            )
            else -> {
                Text("Folio: ${estado.folioMuestraTexto}", style = MaterialTheme.typography.titleLarge)
                FechaEtiquetada(etiqueta = "Registrado", fechaTexto = estado.creadoTexto)

                estado.aguaTexto?.let { Text("Agua: $it %", style = MaterialTheme.typography.bodyMedium) }
                estado.proteinaTexto?.let { Text("Proteína: $it %", style = MaterialTheme.typography.bodyMedium) }
                estado.lactosaTexto?.let { Text("Lactosa: $it %", style = MaterialTheme.typography.bodyMedium) }
                estado.densidadTexto?.let { Text("Densidad: $it", style = MaterialTheme.typography.bodyMedium) }
                estado.temperaturaTexto?.let { Text("Temperatura: $it °C", style = MaterialTheme.typography.bodyMedium) }
                estado.phTexto?.let { Text("pH: $it", style = MaterialTheme.typography.bodyMedium) }
                Text("Agua añadida: ${estado.aguaAnadidaTexto}", style = MaterialTheme.typography.bodyMedium)
                Text("Resultado: ${estado.resultadoTexto ?: NO_DISPONIBLE}", style = MaterialTheme.typography.titleMedium)

                BotonAccionPrincipal(
                    texto = "Registrar corrección",
                    onClick = { viewModel.onEvent(DetalleAnalisisEvent.RegistrarCorreccionPresionado) },
                )
            }
        }
    }
}
