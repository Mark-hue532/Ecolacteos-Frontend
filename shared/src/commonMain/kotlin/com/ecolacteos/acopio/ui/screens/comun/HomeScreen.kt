package com.ecolacteos.acopio.ui.screens.comun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.comun.HomeEffect
import com.ecolacteos.acopio.presentation.comun.HomeEvent
import com.ecolacteos.acopio.presentation.comun.HomeViewModel
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.EstadoVacio
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/** `S-03 · Home` (`MOBILE_SCREENS.md §4`). Sin conexión: banner discreto, nunca bloquea. */
@Composable
fun HomeScreen(
    onNavegarARegistrarVenta: () -> Unit,
    onNavegarAHomeVentas: () -> Unit,
    onNavegarAEstadoSincronizacion: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                HomeEffect.NavegarARegistrarVenta -> onNavegarARegistrarVenta()
                HomeEffect.NavegarAHomeVentas -> onNavegarAHomeVentas()
                HomeEffect.NavegarAEstadoSincronizacion -> onNavegarAEstadoSincronizacion()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        if (!estado.hayConexion) {
            BannerSinConexion(modifier = Modifier.padding(bottom = Espaciado.m.dp))
        }

        Text("Hola, ${estado.nombre}", style = MaterialTheme.typography.headlineMedium)

        Text(
            resumenSyncTexto(estado.resumenSync.pendientes, estado.resumenSync.conError),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable(onClick = { viewModel.onEvent(HomeEvent.EstadoSyncPresionado) })
                .padding(vertical = Espaciado.s.dp),
        )

        if (estado.catalogosVacios) {
            EstadoVacio(
                titulo = "Necesitás conectarte una primera vez",
                explicacion = "Para descargar proveedores y catálogos.",
                textoAccion = "Sincronizar",
                onAccion = { viewModel.onEvent(HomeEvent.SincronizarPresionado) },
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
            return@Column
        }

        if (estado.datosDesactualizados) {
            Text(
                "Los catálogos se actualizaron hace más de 24 horas.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Espaciado.m.dp),
            )
        }

        if (estado.accionPrincipalDisponible) {
            BotonAccionPrincipal(
                texto = estado.etiquetaAccionPrincipal,
                onClick = { viewModel.onEvent(HomeEvent.AccionPrincipalPresionada) },
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
            Text(
                "Ver ventas del día",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = { viewModel.onEvent(HomeEvent.AccesoSecundarioPresionado) })
                    .padding(top = Espaciado.m.dp),
            )
        }
    }
}

private fun resumenSyncTexto(pendientes: Int, conError: Int): String = when {
    conError > 0 -> "$conError con error -- tocá para ver"
    pendientes > 0 -> "$pendientes por enviar"
    else -> "Todo al día"
}
