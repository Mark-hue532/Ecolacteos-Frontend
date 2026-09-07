package com.ecolacteos.acopio.presentation.calidad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.AlertaAnomalia
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerAlertasPorZonaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerZonaAsignadaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerZonasDisponiblesUseCase
import com.ecolacteos.acopio.domain.usecase.ZonaOpcion
import com.ecolacteos.acopio.presentation.formateada
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ESCALA_Z_SCORE = 3

data class AlertaAnomaliaUiState(
    val proveedorNombre: String,
    val tipoTexto: String,
    val severidadTexto: String,
    val zScoreTexto: String?,
    val creadoTexto: String,
)

data class AlertasAnomaliaUiState(
    val zonas: List<ZonaOpcion> = emptyList(),
    val zonaSeleccionada: ZonaOpcion? = null,
    val cargandoZonas: Boolean = true,
    val consultando: Boolean = false,
    val alertas: List<AlertaAnomaliaUiState> = emptyList(),
    val yaConsulto: Boolean = false,
    val mensajeError: String? = null,
    val hayConexion: Boolean = true,
) {
    val vacio: Boolean get() = yaConsulto && !consultando && alertas.isEmpty() && mensajeError == null
}

sealed interface AlertasAnomaliaEvent {
    data class ZonaCambio(val zona: ZonaOpcion) : AlertasAnomaliaEvent
}

/**
 * `C-07 · Alertas de anomalías` (Fase 8E, `MOBILE_SCREENS.md §6`) -- ONLINE-ONLY. `zonaId` es obligatorio
 * en el contrato (`400` sin él, trampa #8): la UI **siempre** exige elegir zona antes de consultar, nunca
 * llama sin ella. El selector sale de `proveedor_cache` (decisión 1 del checkpoint), no de una heurística
 * -- `ObtenerZonaAsignadaUseCase` (`8A`) solo se usa como **preselección**, nunca como la única fuente
 * (decisión 2): si resuelve una zona que está entre las disponibles, se preselecciona y se consulta una
 * vez; si no, el usuario elige a mano.
 */
class AlertasAnomaliaViewModel(
    private val obtenerZonasDisponiblesUseCase: ObtenerZonasDisponiblesUseCase,
    private val obtenerZonaAsignadaUseCase: ObtenerZonaAsignadaUseCase,
    private val obtenerAlertasPorZonaUseCase: ObtenerAlertasPorZonaUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertasAnomaliaUiState())
    val uiState: StateFlow<AlertasAnomaliaUiState> = _uiState.asStateFlow()

    init {
        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val zonas = obtenerZonasDisponiblesUseCase()
            val zonaAsignada = obtenerZonaAsignadaUseCase()
            val preseleccion = zonas.firstOrNull { it.id == zonaAsignada }
            _uiState.update { it.copy(zonas = zonas, cargandoZonas = false, zonaSeleccionada = preseleccion) }
            if (preseleccion != null) consultar(preseleccion)
        }
    }

    fun onEvent(evento: AlertasAnomaliaEvent) {
        when (evento) {
            is AlertasAnomaliaEvent.ZonaCambio -> {
                _uiState.update { it.copy(zonaSeleccionada = evento.zona) }
                viewModelScope.launch { consultar(evento.zona) }
            }
        }
    }

    private suspend fun consultar(zona: ZonaOpcion) {
        _uiState.update { it.copy(consultando = true, mensajeError = null) }
        when (val resultado = obtenerAlertasPorZonaUseCase(zona.id)) {
            is ResultadoDominio.Exito -> _uiState.update {
                it.copy(consultando = false, yaConsulto = true, alertas = resultado.datos.map { alerta -> alerta.aItemUi() })
            }
            is ResultadoDominio.Error -> _uiState.update {
                it.copy(consultando = false, yaConsulto = true, alertas = emptyList(), mensajeError = resultado.error.mensaje)
            }
        }
    }

    private fun AlertaAnomalia.aItemUi(): AlertaAnomaliaUiState = AlertaAnomaliaUiState(
        proveedorNombre = proveedorNombre,
        tipoTexto = tipo.name,
        severidadTexto = severidad.name,
        zScoreTexto = zScore?.aTextoConEscala(ESCALA_Z_SCORE),
        creadoTexto = creadoEn.formateada(),
    )
}
