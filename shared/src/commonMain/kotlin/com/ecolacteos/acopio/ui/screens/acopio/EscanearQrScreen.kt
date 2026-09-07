package com.ecolacteos.acopio.ui.screens.acopio

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
import com.ecolacteos.acopio.plataforma.EscanerQr
import com.ecolacteos.acopio.plataforma.EstadoPermiso
import com.ecolacteos.acopio.plataforma.Permiso
import com.ecolacteos.acopio.plataforma.rememberSolicitanteDePermiso
import com.ecolacteos.acopio.presentation.acopio.EscanearQrEffect
import com.ecolacteos.acopio.presentation.acopio.EscanearQrEvent
import com.ecolacteos.acopio.presentation.acopio.EscanearQrViewModel
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `A-02 · Escanear QR de proveedor` (`MOBILE_SCREENS.md §5`, `§12`). Toda la resolución (SQLite → red →
 * navegación) vive en el `ViewModel`; este `@Composable` solo pide el permiso (en contexto, `§12` regla 1
 * -- recién al entrar acá, nunca antes) y reenvía el texto crudo que decodifica `EscanerQr`.
 */
@Composable
fun EscanearQrScreen(onNavegarARegistrar: (String) -> Unit, onNavegarABuscar: () -> Unit, viewModel: EscanearQrViewModel = koinViewModel()) {
    val estado by viewModel.uiState.collectAsState()

    val solicitarPermiso = rememberSolicitanteDePermiso(Permiso.CAMARA) { resultado ->
        viewModel.onEvent(EscanearQrEvent.PermisoResuelto(resultado))
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                EscanearQrEffect.SolicitarPermiso -> solicitarPermiso()
                is EscanearQrEffect.NavegarARegistrar -> onNavegarARegistrar(efecto.proveedorId)
                EscanearQrEffect.NavegarABuscar -> onNavegarABuscar()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp), verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp)) {
        Text("Escanear QR del proveedor", style = MaterialTheme.typography.headlineMedium)

        when (estado.estadoPermiso) {
            EstadoPermiso.CONCEDIDO -> {
                EscanerQr(
                    onCodigoDetectado = { codigo -> viewModel.onEvent(EscanearQrEvent.CodigoDetectado(codigo)) },
                    modifier = Modifier.fillMaxSize(),
                )
                estado.mensaje?.let { Text(it, color = AcopioColores.atencion, style = MaterialTheme.typography.bodyLarge) }
            }
            EstadoPermiso.DENEGADO_PERMANENTE -> {
                // §12 regla 3: nunca se reintenta el diálogo en bucle -- la única salida son los ajustes.
                Text(
                    "Sin acceso a la cámara. Activalo desde los ajustes del sistema para escanear códigos QR.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                BotonAccionPrincipal(
                    texto = "Abrir ajustes",
                    onClick = { viewModel.onEvent(EscanearQrEvent.IrAAjustesPresionado) },
                )
            }
            EstadoPermiso.DENEGADO, EstadoPermiso.NO_DETERMINADO -> {
                // §12 regla 2: se explica antes de pedir -- este texto es la explicación, el botón dispara el diálogo.
                Text(
                    "Usamos la cámara para leer el código QR del proveedor y precargar sus datos.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                BotonAccionPrincipal(
                    texto = "Permitir cámara",
                    onClick = { viewModel.onEvent(EscanearQrEvent.PermitirCamaraPresionado) },
                )
            }
        }

        Text(
            "Buscar por nombre en cambio",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
                .clickable { viewModel.onEvent(EscanearQrEvent.BuscarPorNombrePresionado) },
        )
    }
}
