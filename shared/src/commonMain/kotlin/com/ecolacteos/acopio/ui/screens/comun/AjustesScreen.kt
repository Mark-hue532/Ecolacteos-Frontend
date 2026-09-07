package com.ecolacteos.acopio.ui.screens.comun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.comun.AjustesEffect
import com.ecolacteos.acopio.presentation.comun.AjustesEvent
import com.ecolacteos.acopio.presentation.comun.AjustesViewModel
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.DialogoConfirmacion
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `S-07 · Ajustes y sesión` (`MOBILE_SCREENS.md §4`). "Nunca existe un camino que borre trabajo no
 * sincronizado sin que el usuario lo haya elegido leyendo exactamente qué se pierde" -- el criterio de
 * aceptación de esta pantalla, literal.
 */
@Composable
fun AjustesScreen(onNavegarAPendientes: () -> Unit, onSesionCerrada: () -> Unit, viewModel: AjustesViewModel = koinViewModel()) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                AjustesEffect.NavegarAPendientes -> onNavegarAPendientes()
                AjustesEffect.NavegarALogin -> onSesionCerrada()
            }
        }
    }

    if (estado.mostrarDialogoBloqueado) {
        DialogoLogoutBloqueado(
            conteo = estado.pendientesConteo,
            hayConexion = estado.hayConexion,
            sincronizando = estado.sincronizando,
            onSincronizarAhora = { viewModel.onEvent(AjustesEvent.SincronizarAhoraPresionado) },
            onVerPendientes = { viewModel.onEvent(AjustesEvent.VerPendientesPresionado) },
            onCerrarSesionIgual = { viewModel.onEvent(AjustesEvent.CerrarSesionIgualPresionado) },
            onCancelar = { viewModel.onEvent(AjustesEvent.CancelarDialogoPresionado) },
        )
    }

    if (estado.mostrarConfirmacionCerrarIgual) {
        DialogoConfirmacion(
            titulo = "Cerrar sesión conservando los datos",
            mensaje = "Tus ${estado.pendientesConteo} registros sin enviar se quedan en este dispositivo. " +
                "Se enviarán solos cuando vuelvas a entrar con este mismo usuario y haya señal.",
            textoConfirmar = "Cerrar sesión igual",
            onConfirmar = { viewModel.onEvent(AjustesEvent.ConfirmarCerrarIgualPresionado) },
            onCancelar = { viewModel.onEvent(AjustesEvent.CancelarDialogoPresionado) },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp), verticalArrangement = Arrangement.spacedBy(Espaciado.s.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(bottom = Espaciado.s.dp))
        }

        Text("${estado.nombre} — ${estado.rol.name}", style = MaterialTheme.typography.titleLarge)
        estado.ultimoSyncTexto?.let { Text("Último sync: $it", style = MaterialTheme.typography.bodyMedium) }
        Text("Versión ${estado.versionApp}", style = MaterialTheme.typography.bodyMedium)

        BotonAccionPrincipal(
            texto = if (estado.cerrandoSesion) "Cerrando sesión..." else "Cerrar sesión",
            onClick = { viewModel.onEvent(AjustesEvent.CerrarSesionPresionado) },
            habilitado = !estado.cerrandoSesion,
            modifier = Modifier.padding(top = Espaciado.l.dp),
        )
    }
}

@Composable
private fun DialogoLogoutBloqueado(
    conteo: Int,
    hayConexion: Boolean,
    sincronizando: Boolean,
    onSincronizarAhora: () -> Unit,
    onVerPendientes: () -> Unit,
    onCerrarSesionIgual: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Tenés $conteo registros sin enviar") },
        text = { Text("Sincronizalos antes de salir, o cerrá sesión conservándolos en este dispositivo.") },
        confirmButton = {
            Column {
                TextButton(onClick = onSincronizarAhora, enabled = hayConexion && !sincronizando) {
                    Text(if (sincronizando) "Sincronizando..." else "Sincronizar ahora")
                }
                Text(
                    "Ver pendientes",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = Espaciado.s.dp).clickable(onClick = onVerPendientes),
                )
                Text(
                    "Cerrar sesión igual",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = onCerrarSesionIgual),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        },
    )
}
