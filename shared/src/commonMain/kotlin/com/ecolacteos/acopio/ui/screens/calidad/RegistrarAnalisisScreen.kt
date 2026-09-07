package com.ecolacteos.acopio.ui.screens.calidad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.calidad.RegistrarAnalisisEffect
import com.ecolacteos.acopio.presentation.calidad.RegistrarAnalisisEvent
import com.ecolacteos.acopio.presentation.calidad.RegistrarAnalisisViewModel
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.CampoDecimal
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `C-03 · Registrar análisis de calidad ★` (Fase 8C, `MOBILE_SCREENS.md §6`). `registroAcopioUuidCliente`/
 * `registroAcopioServerId` vienen de `C-02`, exactamente uno no nulo -- no editables acá (`§0` del prompt).
 * Sin `resultado`: lo calcula el servidor (trampa #9).
 */
@Composable
fun RegistrarAnalisisScreen(
    registroAcopioUuidCliente: String?,
    registroAcopioServerId: String?,
    onGuardadoConExito: () -> Unit,
    viewModel: RegistrarAnalisisViewModel = koinViewModel(parameters = { parametersOf(registroAcopioUuidCliente, registroAcopioServerId) }),
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                RegistrarAnalisisEffect.GuardadoConExito -> onGuardadoConExito()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        item { Text("Registrar análisis", style = MaterialTheme.typography.headlineMedium) }

        if (!estado.hayConexion) {
            item { BannerSinConexion(modifier = Modifier.padding(top = Espaciado.m.dp)) }
        }

        if (estado.hayBorradorParaRetomar) {
            item {
                Column(modifier = Modifier.padding(top = Espaciado.m.dp)) {
                    Text("Tenés un análisis sin terminar, ¿lo retomás?", style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.padding(top = Espaciado.s.dp)) {
                        Text(
                            "Retomar",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.onEvent(RegistrarAnalisisEvent.RetomarBorradorPresionado) },
                        )
                        Text(
                            "Descartar",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = Espaciado.l.dp)
                                .clickable { viewModel.onEvent(RegistrarAnalisisEvent.DescartarBorradorPresionado) },
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = estado.folioMuestra,
                onValueChange = { viewModel.onEvent(RegistrarAnalisisEvent.FolioCambio(it)) },
                label = { Text("Folio de la muestra") },
                isError = estado.errorFolio != null,
                supportingText = estado.errorFolio?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Espaciado.m.dp),
            )
        }

        item {
            Text(
                "Los 6 parámetros de laboratorio son opcionales -- dejalos vacíos si el lactoscan no los reportó.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Espaciado.m.dp),
            )
        }

        item {
            CampoDecimal(
                valor = estado.agua,
                onValorCambia = { viewModel.onEvent(RegistrarAnalisisEvent.AguaCambio(it)) },
                etiqueta = "Agua %",
                error = estado.errorAgua,
                modifier = Modifier.padding(top = Espaciado.s.dp),
            )
        }
        item {
            CampoDecimal(
                valor = estado.proteina,
                onValorCambia = { viewModel.onEvent(RegistrarAnalisisEvent.ProteinaCambio(it)) },
                etiqueta = "Proteína %",
                error = estado.errorProteina,
                modifier = Modifier.padding(top = Espaciado.s.dp),
            )
        }
        item {
            CampoDecimal(
                valor = estado.lactosa,
                onValorCambia = { viewModel.onEvent(RegistrarAnalisisEvent.LactosaCambio(it)) },
                etiqueta = "Lactosa %",
                error = estado.errorLactosa,
                modifier = Modifier.padding(top = Espaciado.s.dp),
            )
        }
        item {
            CampoDecimal(
                valor = estado.densidad,
                onValorCambia = { viewModel.onEvent(RegistrarAnalisisEvent.DensidadCambio(it)) },
                etiqueta = "Densidad",
                error = estado.errorDensidad,
                modifier = Modifier.padding(top = Espaciado.s.dp),
            )
        }
        item {
            CampoDecimal(
                valor = estado.temperatura,
                onValorCambia = { viewModel.onEvent(RegistrarAnalisisEvent.TemperaturaCambio(it)) },
                etiqueta = "Temperatura °C",
                error = estado.errorTemperatura,
                modifier = Modifier.padding(top = Espaciado.s.dp),
            )
        }
        item {
            CampoDecimal(
                valor = estado.ph,
                onValorCambia = { viewModel.onEvent(RegistrarAnalisisEvent.PhCambio(it)) },
                etiqueta = "pH",
                error = estado.errorPh,
                modifier = Modifier.padding(top = Espaciado.s.dp),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Espaciado.m.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("¿Agua añadida?", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = estado.aguaAnadida,
                    onCheckedChange = { viewModel.onEvent(RegistrarAnalisisEvent.AguaAnadidaCambio(it)) },
                )
            }
        }

        estado.mensajePadreNoResoluble?.let { mensaje ->
            item { Text(mensaje, color = AcopioColores.error, modifier = Modifier.padding(top = Espaciado.m.dp)) }
        }

        item {
            BotonAccionPrincipal(
                texto = if (estado.guardando) "Guardando..." else "Guardar",
                onClick = { viewModel.onEvent(RegistrarAnalisisEvent.GuardarPresionado) },
                habilitado = estado.puedeGuardar,
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
        }
    }
}
