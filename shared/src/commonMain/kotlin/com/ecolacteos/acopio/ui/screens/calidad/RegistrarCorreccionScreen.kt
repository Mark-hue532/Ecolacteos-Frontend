package com.ecolacteos.acopio.ui.screens.calidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.NO_DISPONIBLE
import com.ecolacteos.acopio.presentation.calidad.RegistrarCorreccionEffect
import com.ecolacteos.acopio.presentation.calidad.RegistrarCorreccionEvent
import com.ecolacteos.acopio.presentation.calidad.RegistrarCorreccionViewModel
import com.ecolacteos.acopio.ui.components.BloqueoOnlineOnly
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.CampoDecimal
import com.ecolacteos.acopio.ui.components.DialogoConfirmacion
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `C-06 · Registrar corrección de litros` (Fase 8E, `MOBILE_SCREENS.md §6`, ONLINE-ONLY). Destino
 * compartido por `A-06` y `C-04`. Sin conexión: bloqueo total (`§6`, trampa #1 -- nunca se encola). La
 * confirmación es obligatoria antes de enviar (`§6`).
 */
@Composable
fun RegistrarCorreccionScreen(
    registroAcopioId: String,
    onGuardadoConExito: () -> Unit,
    viewModel: RegistrarCorreccionViewModel = koinViewModel(parameters = { parametersOf(registroAcopioId) }),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                RegistrarCorreccionEffect.GuardadoConExito -> onGuardadoConExito()
            }
        }
    }

    if (!estado.hayConexion) {
        BloqueoOnlineOnly(mensaje = "Requiere conexión para registrar una corrección.")
        return
    }

    if (estado.mostrarConfirmacion) {
        DialogoConfirmacion(
            titulo = "Confirmar corrección",
            mensaje = "Se va a corregir de ${estado.litrosActualTexto ?: NO_DISPONIBLE} L a ${estado.litrosCorregidoTexto} L. No se puede deshacer.",
            textoConfirmar = "Confirmar",
            onConfirmar = { viewModel.onEvent(RegistrarCorreccionEvent.ConfirmarPresionado) },
            onCancelar = { viewModel.onEvent(RegistrarCorreccionEvent.CancelarConfirmacionPresionado) },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp),
        verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp),
    ) {
        Text("Registrar corrección", style = MaterialTheme.typography.headlineMedium)

        if (estado.cargandoActual) {
            CircularProgressIndicator()
        } else {
            Text("Litros actuales: ${estado.litrosActualTexto ?: NO_DISPONIBLE}", style = MaterialTheme.typography.bodyLarge)

            CampoDecimal(
                valor = estado.litrosCorregidoTexto,
                onValorCambia = { viewModel.onEvent(RegistrarCorreccionEvent.LitrosCorregidoCambio(it)) },
                etiqueta = "Litros corregidos",
                error = estado.errorLitrosCorregido,
            )

            OutlinedTextField(
                value = estado.motivoTexto,
                onValueChange = { viewModel.onEvent(RegistrarCorreccionEvent.MotivoCambio(it)) },
                label = { Text("Motivo (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            estado.mensajeError?.let { Text(it, color = AcopioColores.error, style = MaterialTheme.typography.bodyMedium) }

            BotonAccionPrincipal(
                texto = if (estado.enviando) "Enviando..." else "Enviar",
                onClick = { viewModel.onEvent(RegistrarCorreccionEvent.EnviarPresionado) },
                habilitado = estado.puedeEnviar,
            )
        }
    }
}
