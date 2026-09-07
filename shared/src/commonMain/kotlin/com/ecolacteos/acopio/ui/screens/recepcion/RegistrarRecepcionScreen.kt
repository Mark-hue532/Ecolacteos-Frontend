package com.ecolacteos.acopio.ui.screens.recepcion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.recepcion.RegistrarRecepcionEffect
import com.ecolacteos.acopio.presentation.recepcion.RegistrarRecepcionEvent
import com.ecolacteos.acopio.presentation.recepcion.RegistrarRecepcionViewModel
import com.ecolacteos.acopio.ui.components.BloqueoOnlineOnly
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.CampoDecimal
import com.ecolacteos.acopio.ui.components.SelectorCatalogo
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `R-01 · Registrar recepción en planta ★` (Fase 8E, `MOBILE_SCREENS.md §9`, ONLINE-ONLY). Sin conexión:
 * bloqueo total, sin borrador ni cola (`DATA-006`, trampa #1). El 409 (`R-02b`) se resuelve acá mismo con
 * un diálogo -- nunca reintentando el `POST`.
 */
@Composable
fun RegistrarRecepcionScreen(
    onGuardadoConExito: (String) -> Unit,
    viewModel: RegistrarRecepcionViewModel = koinViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is RegistrarRecepcionEffect.NavegarAResultado -> onGuardadoConExito(efecto.id)
            }
        }
    }

    if (!estado.hayConexion) {
        BloqueoOnlineOnly(mensaje = "Requiere conexión para registrar una recepción.")
        return
    }

    estado.conflicto?.let { conflicto ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(RegistrarRecepcionEvent.CancelarConflictoPresionado) },
            title = { Text("Ya existe una recepción") },
            text = { Text("Ya existe una recepción para esta unidad, fecha y turno (${conflicto.turnoReal}).") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onEvent(RegistrarRecepcionEvent.VerRegistroExistentePresionado) },
                    enabled = conflicto.recepcionExistente != null,
                ) { Text("Ver el registro existente") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { viewModel.onEvent(RegistrarRecepcionEvent.CambiarTurnoPresionado) }) { Text("Cambiar el turno") }
                    TextButton(onClick = { viewModel.onEvent(RegistrarRecepcionEvent.CancelarConflictoPresionado) }) { Text("Cancelar") }
                }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp),
        verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp),
    ) {
        Text("Registrar recepción en planta", style = MaterialTheme.typography.headlineMedium)

        Text("Fecha: ${estado.fechaTexto}", style = MaterialTheme.typography.bodyLarge)

        SelectorCatalogo(
            opciones = estado.unidades,
            seleccionado = estado.unidadSeleccionada,
            onSeleccion = { viewModel.onEvent(RegistrarRecepcionEvent.UnidadCambio(it)) },
            etiquetaTexto = { it.placa },
            etiqueta = "Unidad",
            error = estado.errorUnidad,
        )

        OutlinedTextField(
            value = estado.turnoTexto,
            onValueChange = { viewModel.onEvent(RegistrarRecepcionEvent.TurnoCambio(it)) },
            label = { Text("Turno (opcional -- el servidor aplica \"UNICO\" si queda vacío)") },
            modifier = Modifier.fillMaxWidth(),
        )

        CampoDecimal(
            valor = estado.litrosCampoTexto,
            onValorCambia = { viewModel.onEvent(RegistrarRecepcionEvent.LitrosCampoCambio(it)) },
            etiqueta = "Litros de campo",
            error = estado.errorLitrosCampo,
        )

        CampoDecimal(
            valor = estado.litrosPlantaTexto,
            onValorCambia = { viewModel.onEvent(RegistrarRecepcionEvent.LitrosPlantaCambio(it)) },
            etiqueta = "Litros de planta",
            error = estado.errorLitrosPlanta,
        )

        estado.mensajeError?.let { Text(it, color = AcopioColores.error, style = MaterialTheme.typography.bodyMedium) }

        BotonAccionPrincipal(
            texto = if (estado.enviando) "Guardando..." else "Guardar",
            onClick = { viewModel.onEvent(RegistrarRecepcionEvent.GuardarPresionado) },
            habilitado = estado.puedeGuardar,
        )
    }
}
