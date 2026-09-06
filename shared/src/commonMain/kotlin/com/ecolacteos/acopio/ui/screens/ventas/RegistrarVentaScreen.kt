package com.ecolacteos.acopio.ui.screens.ventas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.ventas.OPCIONES_TIPO_CLIENTE
import com.ecolacteos.acopio.presentation.ventas.RegistrarVentaEffect
import com.ecolacteos.acopio.presentation.ventas.RegistrarVentaEvent
import com.ecolacteos.acopio.presentation.ventas.RegistrarVentaViewModel
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.CampoDecimal
import com.ecolacteos.acopio.ui.components.SelectorCatalogo
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `V-02 · Registrar venta ★` (`MOBILE_SCREENS.md §8`). Destino de pantalla completa (`§2.1` regla 2). Al
 * guardar, vuelve hacia atrás con `Snackbar` -- acá delegado a [onGuardadoConExito], que el `NavGraph`
 * resuelve como `popBackStack()` + mensaje (`§2.1` regla 3).
 */
@Composable
fun RegistrarVentaScreen(onGuardadoConExito: () -> Unit, viewModel: RegistrarVentaViewModel = koinViewModel()) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                RegistrarVentaEffect.GuardadoConExito -> onGuardadoConExito()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp), verticalArrangement = Arrangement.spacedBy(Espaciado.m.dp)) {
        item { Text("Registrar venta", style = MaterialTheme.typography.headlineMedium) }

        if (!estado.hayConexion) {
            item { BannerSinConexion() }
        }

        if (estado.hayBorradorParaRetomar) {
            item {
                Column {
                    Text("Tenés un registro sin terminar, ¿lo retomás?", style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.padding(top = Espaciado.s.dp)) {
                        Text(
                            "Retomar",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.onEvent(RegistrarVentaEvent.RetomarBorradorPresionado) },
                        )
                        Text(
                            "Descartar",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = Espaciado.l.dp)
                                .clickable { viewModel.onEvent(RegistrarVentaEvent.DescartarBorradorPresionado) },
                        )
                    }
                }
            }
        }

        item { Text("Fecha: ${estado.fechaTexto}", style = MaterialTheme.typography.bodyLarge) }

        item {
            // Selector cerrado de exactamente 3 opciones (DATA-010) -- nunca texto libre.
            SelectorCatalogo(
                opciones = OPCIONES_TIPO_CLIENTE,
                seleccionado = estado.tipoClienteSeleccionado,
                onSeleccion = { viewModel.onEvent(RegistrarVentaEvent.TipoClienteCambio(it)) },
                etiquetaTexto = { it.name },
                etiqueta = "Tipo de cliente",
                error = estado.errorTipoCliente,
            )
        }

        item {
            SelectorCatalogo(
                opciones = estado.tiposQueso,
                seleccionado = estado.tipoQuesoSeleccionado,
                onSeleccion = { viewModel.onEvent(RegistrarVentaEvent.TipoQuesoCambio(it)) },
                etiquetaTexto = { it.nombre },
                etiqueta = "Tipo de queso",
                error = estado.errorTipoQueso,
            )
        }

        item {
            CampoDecimal(
                valor = estado.cantidadTexto,
                onValorCambia = { viewModel.onEvent(RegistrarVentaEvent.CantidadCambio(it)) },
                etiqueta = "Cantidad",
                error = estado.errorCantidad,
            )
        }

        item {
            CampoDecimal(
                valor = estado.precioUnitarioTexto,
                onValorCambia = { viewModel.onEvent(RegistrarVentaEvent.PrecioCambio(it)) },
                etiqueta = "Precio unitario (S/)",
                error = estado.errorPrecio,
            )
        }

        estado.subtotalEstimadoTexto?.let { subtotal ->
            item { Text(subtotal, style = MaterialTheme.typography.bodyMedium) }
        }

        estado.errorGeneral?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }

        item {
            BotonAccionPrincipal(
                texto = if (estado.guardando) "Guardando..." else "Guardar",
                onClick = { viewModel.onEvent(RegistrarVentaEvent.GuardarPresionado) },
                habilitado = estado.puedeGuardar,
            )
        }
    }
}
