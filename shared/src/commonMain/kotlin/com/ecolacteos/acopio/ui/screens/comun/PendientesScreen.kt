package com.ecolacteos.acopio.ui.screens.comun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.comun.ItemPendiente
import com.ecolacteos.acopio.presentation.comun.PendientesEffect
import com.ecolacteos.acopio.presentation.comun.PendientesEvent
import com.ecolacteos.acopio.presentation.comun.PendientesViewModel
import com.ecolacteos.acopio.presentation.comun.esperaConAdvertencia
import com.ecolacteos.acopio.ui.components.BadgeEstadoSync
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.DialogoConfirmacion
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `S-05 · Pendientes` (`MOBILE_SCREENS.md §4`). Tres secciones en orden fijo: con error arriba (exige
 * acción), esperando dependencia al medio (solo ver, nunca en rojo -- `§10.5`), por enviar abajo.
 */
@Composable
fun PendientesScreen(
    onNavegarAEditarAcopio: (String) -> Unit,
    onNavegarAEditarVenta: (String) -> Unit,
    viewModel: PendientesViewModel = koinViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is PendientesEffect.NavegarAEditarAcopio -> onNavegarAEditarAcopio(efecto.uuidCliente)
                is PendientesEffect.NavegarAEditarVenta -> onNavegarAEditarVenta(efecto.uuidCliente)
            }
        }
    }

    estado.pendienteADescartar?.let { item ->
        DialogoConfirmacion(
            titulo = "Descartar registro",
            mensaje = "Se va a borrar: ${item.resumen}. No se puede deshacer.",
            textoConfirmar = "Descartar",
            onConfirmar = { viewModel.onEvent(PendientesEvent.ConfirmarDescartePresionado) },
            onCancelar = { viewModel.onEvent(PendientesEvent.CancelarDescartePresionado) },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        Text("Pendientes", style = MaterialTheme.typography.headlineMedium)

        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(top = Espaciado.m.dp))
        }

        when {
            estado.vacio -> EstadoVacio(
                titulo = "No tenés registros pendientes",
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
            else -> LazyColumn(modifier = Modifier.padding(top = Espaciado.m.dp)) {
                if (estado.conError.isNotEmpty()) {
                    item { TituloSeccion("Con error") }
                    items(estado.conError) { fila ->
                        FilaPendiente(fila, viewModel::onEvent)
                        HorizontalDivider()
                    }
                }
                if (estado.esperandoDependencia.isNotEmpty()) {
                    item { TituloSeccion("Esperando otra entrega") }
                    items(estado.esperandoDependencia) { fila ->
                        FilaPendiente(fila, viewModel::onEvent)
                        HorizontalDivider()
                    }
                }
                if (estado.porEnviar.isNotEmpty()) {
                    item { TituloSeccion(if (estado.sincronizando) "Por enviar (sincronizando...)" else "Por enviar") }
                    items(estado.porEnviar) { fila ->
                        FilaPendiente(fila, viewModel::onEvent)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TituloSeccion(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Espaciado.m.dp))
}

@Composable
private fun FilaPendiente(item: ItemPendiente, onEvent: (PendientesEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Espaciado.s.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(item.resumen, style = MaterialTheme.typography.bodyLarge)
            BadgeEstadoSync(item.estado)
        }

        // El motivo del backend se muestra literal, sin reinterpretar (§4 regla 1, trampa #4).
        item.motivoError?.let { motivo ->
            Text(motivo, color = AcopioColores.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (item.esperaConAdvertencia) {
            Text(
                "Lleva ${item.diasEsperando} días esperando",
                color = AcopioColores.atencion,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(modifier = Modifier.padding(top = Espaciado.s.dp)) {
            if (item.motivoError != null) {
                Text(
                    "Reintentar",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onEvent(PendientesEvent.ReintentarPresionado(item)) },
                )
                if (item.puedeEditar) {
                    Text(
                        "Editar",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Espaciado.l.dp)
                            .clickable { onEvent(PendientesEvent.EditarPresionado(item)) },
                    )
                }
                Text(
                    "Descartar",
                    color = AcopioColores.error,
                    modifier = Modifier.padding(start = Espaciado.l.dp)
                        .clickable { onEvent(PendientesEvent.DescartarPresionado(item)) },
                )
            }
        }
    }
}
