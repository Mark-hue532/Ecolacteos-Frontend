package com.ecolacteos.acopio.ui.screens.acopio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.ecolacteos.acopio.plataforma.Permiso
import com.ecolacteos.acopio.plataforma.rememberSolicitanteDePermiso
import com.ecolacteos.acopio.presentation.acopio.EstadoGps
import com.ecolacteos.acopio.presentation.acopio.RegistrarAcopioEffect
import com.ecolacteos.acopio.presentation.acopio.RegistrarAcopioEvent
import com.ecolacteos.acopio.presentation.acopio.RegistrarAcopioViewModel
import com.ecolacteos.acopio.ui.components.BannerSinConexion
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.components.CampoDecimal
import com.ecolacteos.acopio.ui.components.SelectorCatalogo
import com.ecolacteos.acopio.ui.theme.AcopioColores
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * `A-04 · Registrar acopio ★` (`MOBILE_SCREENS.md §5`). La pantalla central del producto -- hermana menor
 * de `V-02` (`ui/screens/ventas/RegistrarVentaScreen.kt`). El permiso de ubicación se pide automáticamente
 * al entrar (`§5`: "se pide al abrir la pantalla, en paralelo"), sin pantalla de explicación previa (a
 * diferencia de `A-02`): es no bloqueante y de bajo riesgo, el contexto ya es evidente.
 */
@Composable
fun RegistrarAcopioScreen(
    proveedorId: String,
    onGuardadoConExito: () -> Unit,
    onNavegarAHistorial: (String) -> Unit,
    viewModel: RegistrarAcopioViewModel = koinViewModel(parameters = { parametersOf(proveedorId) }),
) {
    val estado by viewModel.uiState.collectAsState()

    val solicitarPermisoUbicacion = rememberSolicitanteDePermiso(Permiso.UBICACION) { resultado ->
        viewModel.onEvent(RegistrarAcopioEvent.PermisoUbicacionResuelto(resultado))
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                RegistrarAcopioEffect.GuardadoConExito -> onGuardadoConExito()
                RegistrarAcopioEffect.SolicitarPermisoUbicacion -> solicitarPermisoUbicacion()
                RegistrarAcopioEffect.AvisoGpsNoDisponible -> Unit // el estado de gps ya lo muestra la pantalla
                is RegistrarAcopioEffect.NavegarAHistorial -> onNavegarAHistorial(efecto.proveedorId)
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp)) {
        item { Text("Registrar acopio", style = MaterialTheme.typography.headlineMedium) }

        if (!estado.hayConexion) {
            item { BannerSinConexion(modifier = Modifier.padding(top = Espaciado.m.dp)) }
        }

        if (estado.hayBorradorParaRetomar) {
            item {
                Column(modifier = Modifier.padding(top = Espaciado.m.dp)) {
                    Text("Tenés un registro sin terminar, ¿lo retomás?", style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.padding(top = Espaciado.s.dp)) {
                        Text(
                            "Retomar",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.onEvent(RegistrarAcopioEvent.RetomarBorradorPresionado) },
                        )
                        Text(
                            "Descartar",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = Espaciado.l.dp)
                                .clickable { viewModel.onEvent(RegistrarAcopioEvent.DescartarBorradorPresionado) },
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = Espaciado.m.dp)) {
                Text(
                    estado.proveedor?.nombre ?: "Proveedor: cargando...",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Ver historial de este proveedor",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { viewModel.onEvent(RegistrarAcopioEvent.VerHistorialPresionado) },
                )
            }
        }

        estado.errorGeneral?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Espaciado.s.dp)) }
        }

        item {
            SelectorCatalogo(
                opciones = estado.unidades,
                seleccionado = estado.unidadSeleccionada,
                onSeleccion = { viewModel.onEvent(RegistrarAcopioEvent.UnidadCambio(it)) },
                etiquetaTexto = { it.placa },
                etiqueta = "Unidad",
                error = estado.errorUnidad,
                modifier = Modifier.padding(top = Espaciado.m.dp),
            )
        }

        item {
            Text(
                "Fecha: ${estado.fechaHoraTexto}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Espaciado.m.dp),
            )
            estado.avisoFechaPasada?.let { Text(it, color = AcopioColores.atencion, style = MaterialTheme.typography.bodyMedium) }
        }

        item {
            CampoDecimal(
                valor = estado.litrosTexto,
                onValorCambia = { viewModel.onEvent(RegistrarAcopioEvent.LitrosCambio(it)) },
                etiqueta = "Litros",
                error = estado.errorLitros,
                modifier = Modifier.padding(top = Espaciado.m.dp),
            )
        }

        item {
            SelectorCatalogo(
                opciones = estado.motivos,
                seleccionado = estado.motivoSeleccionado,
                onSeleccion = { viewModel.onEvent(RegistrarAcopioEvent.MotivoCambio(it)) },
                etiquetaTexto = { it.descripcion },
                etiqueta = "Motivo de observación (opcional)",
                modifier = Modifier.padding(top = Espaciado.m.dp),
            )
        }

        item { TextoGps(estado.gps, modifier = Modifier.padding(top = Espaciado.m.dp)) }

        item {
            BotonAccionPrincipal(
                texto = if (estado.guardando) "Guardando..." else "Guardar",
                onClick = { viewModel.onEvent(RegistrarAcopioEvent.GuardarPresionado) },
                habilitado = estado.puedeGuardar,
                modifier = Modifier.padding(top = Espaciado.l.dp),
            )
        }
    }
}

/** GPS nunca bloquea (`§5` regla 4) -- este texto es puramente informativo, nunca condiciona [BotonAccionPrincipal]. */
@Composable
private fun TextoGps(gps: EstadoGps, modifier: Modifier = Modifier) {
    val texto = when (gps) {
        EstadoGps.Buscando -> "Buscando ubicación..."
        is EstadoGps.Obtenido -> "Ubicación: ${gps.latTexto}, ${gps.lngTexto}"
        EstadoGps.NoDisponible -> "Sin ubicación disponible -- se guarda igual"
        EstadoGps.SinPermiso -> "Sin permiso de ubicación -- se guarda igual"
    }
    Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}
