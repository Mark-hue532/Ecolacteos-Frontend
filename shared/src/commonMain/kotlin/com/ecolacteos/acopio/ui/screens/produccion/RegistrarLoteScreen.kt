package com.ecolacteos.acopio.ui.screens.produccion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.ecolacteos.acopio.presentation.produccion.RegistrarLoteEffect
import com.ecolacteos.acopio.presentation.produccion.RegistrarLoteEvent
import com.ecolacteos.acopio.presentation.produccion.RegistrarLoteViewModel
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.CampoDecimal
import com.ecolacteos.acopio.ui.components.SelectorCatalogo
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `P-03 · Registrar lote de producción ★` (Fase 8D, `MOBILE_SCREENS.md §7`). Las entregas vienen resueltas
 * de `P-02` -- acá no se eligen, solo se ve el total como ayuda (`§4.3`, nunca autocompletado).
 */
@Composable
fun RegistrarLoteScreen(
    registroAcopioUuidClientes: List<String>,
    registroAcopioServerIds: List<String>,
    totalLitrosSeleccionadoTexto: String,
    onGuardadoConExito: () -> Unit,
    viewModel: RegistrarLoteViewModel = koinViewModel(
        parameters = { parametersOf(registroAcopioUuidClientes, registroAcopioServerIds, totalLitrosSeleccionadoTexto) },
    ),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                RegistrarLoteEffect.GuardadoConExito -> onGuardadoConExito()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        item { Text("Registrar lote", style = MaterialTheme.typography.headlineMedium) }

        if (!estado.hayConexion) {
            item { BannerSinConexion(modifier = Modifier.padding(top = Espaciado.m.dp)) }
        }

        if (estado.hayBorradorParaRetomar) {
            item {
                Column(modifier = Modifier.padding(top = Espaciado.m.dp)) {
                    Text("Tenés un lote sin terminar, ¿lo retomás?", style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.padding(top = Espaciado.s.dp)) {
                        Text(
                            "Retomar",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.onEvent(RegistrarLoteEvent.RetomarBorradorPresionado) },
                        )
                        Text(
                            "Descartar",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = Espaciado.l.dp)
                                .clickable { viewModel.onEvent(RegistrarLoteEvent.DescartarBorradorPresionado) },
                        )
                    }
                }
            }
        }

        item {
            Text("Fecha: ${estado.fechaTexto}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Espaciado.m.dp))
            Text(
                "Entregas seleccionadas -- total: ${estado.totalLitrosSeleccionadoTexto} L",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            SelectorCatalogo(
                opciones = estado.tiposQueso,
                seleccionado = estado.tipoQuesoSeleccionado,
                onSeleccion = { viewModel.onEvent(RegistrarLoteEvent.TipoQuesoCambio(it)) },
                etiquetaTexto = { it.nombre },
                etiqueta = "Tipo de queso",
                error = estado.errorTipoQueso,
                modifier = Modifier.padding(top = Espaciado.m.dp),
            )
        }

        item {
            CampoDecimal(
                valor = estado.litrosUsadosTexto,
                onValorCambia = { viewModel.onEvent(RegistrarLoteEvent.LitrosUsadosCambio(it)) },
                etiqueta = "Litros usados (ayuda: ${estado.totalLitrosSeleccionadoTexto} L seleccionados)",
                error = estado.errorLitrosUsados,
                modifier = Modifier.padding(top = Espaciado.m.dp),
            )
        }

        item {
            CampoDecimal(
                valor = estado.unidadesObtenidasTexto,
                onValorCambia = { viewModel.onEvent(RegistrarLoteEvent.UnidadesObtenidasCambio(it)) },
                etiqueta = "Unidades obtenidas",
                error = estado.errorUnidadesObtenidas,
                modifier = Modifier.padding(top = Espaciado.m.dp),
            )
        }

        estado.mensajePadreNoResoluble?.let { mensaje ->
            item { Text(mensaje, color = AcopioColores.error, modifier = Modifier.padding(top = Espaciado.m.dp)) }
        }

        item {
            BotonAccionPrincipal(
                texto = if (estado.guardando) "Guardando..." else "Guardar",
                onClick = { viewModel.onEvent(RegistrarLoteEvent.GuardarPresionado) },
                habilitado = estado.puedeGuardar,
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
        }
    }
}
