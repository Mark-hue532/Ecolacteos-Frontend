package com.ecolacteos.acopio.ui.screens.comun

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.comun.EstadoSincronizacionEvent
import com.ecolacteos.acopio.presentation.comun.EstadoSincronizacionViewModel
import com.ecolacteos.acopio.presentation.comun.RecursoSyncUiState
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `S-04 · Estado de sincronización` (`MOBILE_SCREENS.md §4`). "Todo al día" es un vacío **positivo**; "N
 * esperando" **no** es un error (`§10.5`, trampa #9).
 */
@Composable
fun EstadoSincronizacionScreen(viewModel: EstadoSincronizacionViewModel = koinViewModel()) {
    val estado by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(bottom = Espaciado.m.dp))
        }

        Text("Estado de sincronización", style = MaterialTheme.typography.headlineMedium)

        if (estado.todoAlDia) {
            EstadoVacio(
                titulo = "Todo al día",
                explicacion = estado.ultimoSyncTexto?.let { "Último sync: $it" },
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
        } else {
            if (!estado.hayConexion) {
                Text(
                    "${estado.totalPendiente} registros esperando señal",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Espaciado.m.dp),
                )
            }
            FilaRecurso("Registros de acopio", estado.registroAcopio)
            FilaRecurso("Análisis de calidad", estado.analisisCalidad)
            FilaRecurso("Lotes de producción", estado.loteProduccion)
            FilaRecurso("Ventas", estado.venta)
        }

        BotonAccionPrincipal(
            texto = if (estado.sincronizandoAhora) "Sincronizando..." else "Sincronizar ahora",
            onClick = { viewModel.onEvent(EstadoSincronizacionEvent.SincronizarAhoraPresionado) },
            habilitado = estado.hayConexion && !estado.sincronizandoAhora,
            modifier = Modifier.padding(top = Espaciado.l.dp),
        )
    }
}

@Composable
private fun FilaRecurso(nombre: String, recurso: RecursoSyncUiState) {
    if (recurso.total == 0) return
    Column(modifier = Modifier.padding(top = Espaciado.m.dp)) {
        Text(nombre, style = MaterialTheme.typography.titleLarge)
        if (recurso.pendientes > 0) Text("${recurso.pendientes} por enviar")
        if (recurso.sincronizando > 0) Text("${recurso.sincronizando} sincronizando")
        // PENDING_DEPENDENCY nunca en rojo -- informativo (§10.5, trampa #9).
        if (recurso.enEspera > 0) Text("${recurso.enEspera} esperando otra entrega")
        if (recurso.conError > 0) Text("${recurso.conError} con error", color = MaterialTheme.colorScheme.error)
    }
}
