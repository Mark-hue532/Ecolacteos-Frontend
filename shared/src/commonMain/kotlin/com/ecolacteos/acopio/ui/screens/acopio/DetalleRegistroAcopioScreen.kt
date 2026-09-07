package com.ecolacteos.acopio.ui.screens.acopio

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
import com.ecolacteos.acopio.presentation.NO_DISPONIBLE
import com.ecolacteos.acopio.presentation.acopio.DetalleRegistroAcopioEffect
import com.ecolacteos.acopio.presentation.acopio.DetalleRegistroAcopioEvent
import com.ecolacteos.acopio.presentation.acopio.DetalleRegistroAcopioViewModel
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.components.FechaEtiquetada
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `A-06 · Detalle de registro de acopio` (`MOBILE_SCREENS.md §5`, ONLINE+CACHE). `fechaHora`/`sincronizadoEn`
 * se muestran etiquetados y por separado (`§10.3`, `DATA-012`) -- nunca una duración entre ambos.
 */
@Composable
fun DetalleRegistroAcopioScreen(
    id: String,
    onNavegarARegistrarCorreccion: (String) -> Unit,
    viewModel: DetalleRegistroAcopioViewModel = koinViewModel(parameters = { parametersOf(id) }),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is DetalleRegistroAcopioEffect.NavegarARegistrarCorreccion -> onNavegarARegistrarCorreccion(efecto.registroAcopioId)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp),
        verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp),
    ) {
        Text("Detalle del registro", style = MaterialTheme.typography.headlineMedium)

        when {
            estado.cargando -> CircularProgressIndicator()
            !estado.encontrada -> EstadoVacio(
                titulo = "No se pudo cargar el detalle",
                explicacion = "Revisá tu conexión e intentá de nuevo.",
                textoAccion = "Reintentar",
                onAccion = { viewModel.onEvent(DetalleRegistroAcopioEvent.ReintentarPresionado) },
            )
            else -> {
                Text(estado.proveedorNombreTexto, style = MaterialTheme.typography.titleLarge)
                Text("${estado.litrosTexto} L", style = MaterialTheme.typography.bodyLarge)
                FechaEtiquetada(etiqueta = "Capturado", fechaTexto = estado.fechaCapturadoTexto)
                FechaEtiquetada(etiqueta = "Sincronizado", fechaTexto = estado.sincronizadoTexto ?: NO_DISPONIBLE)

                if (estado.gpsLatTexto != null && estado.gpsLngTexto != null) {
                    Text("Ubicación: ${estado.gpsLatTexto}, ${estado.gpsLngTexto}", style = MaterialTheme.typography.bodyMedium)
                }

                estado.motivoObservacionTexto?.let { Text("Motivo: $it", style = MaterialTheme.typography.bodyMedium) }

                if (estado.puedeRegistrarCorreccion) {
                    BotonAccionPrincipal(
                        texto = "Registrar corrección",
                        onClick = { viewModel.onEvent(DetalleRegistroAcopioEvent.RegistrarCorreccionPresionado) },
                    )
                }
            }
        }
    }
}
