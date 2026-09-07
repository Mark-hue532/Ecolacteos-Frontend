package com.ecolacteos.acopio.presentation.calidad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.data.repository.ResultadoBuscarAnalisisPorFolio
import com.ecolacteos.acopio.domain.model.AnalisisCalidadDetalle
import com.ecolacteos.acopio.domain.usecase.BuscarAnalisisPorFolioUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.presentation.formateadoConEscala
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ESCALA_LABORATORIO = 2
private const val MAX_FOLIO = 40

data class ResultadoFolioUiState(
    val folioMuestraTexto: String,
    val aguaTexto: String?,
    val proteinaTexto: String?,
    val lactosaTexto: String?,
    val densidadTexto: String?,
    val temperaturaTexto: String?,
    val phTexto: String?,
    val aguaAnadidaTexto: String,
    val resultadoTexto: String?,
)

data class BuscarAnalisisPorFolioUiState(
    val folioTexto: String = "",
    val buscando: Boolean = false,
    val resultado: ResultadoFolioUiState? = null,
    val sinResultados: Boolean = false,
    val mensajeError: String? = null,
    val hayConexion: Boolean = true,
) {
    /** `C-05`: sin conexión queda deshabilitado (`§6`), no solo bloqueado al enviar. */
    val puedeBuscar: Boolean get() = hayConexion && !buscando && folioTexto.isNotBlank() && folioTexto.length <= MAX_FOLIO
}

sealed interface BuscarAnalisisPorFolioEvent {
    data class FolioCambio(val texto: String) : BuscarAnalisisPorFolioEvent
    data object BuscarPresionado : BuscarAnalisisPorFolioEvent
}

/**
 * `C-05 · Buscar análisis por folio` (Fase 8E, `MOBILE_SCREENS.md §6`, ONLINE-ONLY). El folio **no es un
 * UUID** -- texto libre de hasta 40 caracteres. Sin resultados es estado vacío, nunca un error (trampa
 * implícita del `§10.4`: un 404 de negocio no es lo mismo que un fallo de red).
 */
class BuscarAnalisisPorFolioViewModel(
    private val buscarAnalisisPorFolioUseCase: BuscarAnalisisPorFolioUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuscarAnalisisPorFolioUiState())
    val uiState: StateFlow<BuscarAnalisisPorFolioUiState> = _uiState.asStateFlow()

    init {
        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(evento: BuscarAnalisisPorFolioEvent) {
        when (evento) {
            is BuscarAnalisisPorFolioEvent.FolioCambio ->
                _uiState.update { it.copy(folioTexto = evento.texto, resultado = null, sinResultados = false, mensajeError = null) }
            BuscarAnalisisPorFolioEvent.BuscarPresionado -> buscar()
        }
    }

    private fun buscar() {
        // Defensa en profundidad, no solo el campo deshabilitado de la UI (mismo criterio que las demás
        // pantallas online-only de esta sub-fase): sin conexión no se dispara ninguna request.
        if (!_uiState.value.puedeBuscar) return
        val folio = _uiState.value.folioTexto
        _uiState.update { it.copy(buscando = true, resultado = null, sinResultados = false, mensajeError = null) }
        viewModelScope.launch {
            when (val resultado = buscarAnalisisPorFolioUseCase(folio)) {
                is ResultadoBuscarAnalisisPorFolio.Encontrado ->
                    _uiState.update { it.copy(buscando = false, resultado = resultado.detalle.aItemUi()) }
                ResultadoBuscarAnalisisPorFolio.NoEncontrado ->
                    _uiState.update { it.copy(buscando = false, sinResultados = true) }
                is ResultadoBuscarAnalisisPorFolio.Error ->
                    _uiState.update { it.copy(buscando = false, mensajeError = resultado.error.mensaje) }
            }
        }
    }

    private fun AnalisisCalidadDetalle.aItemUi(): ResultadoFolioUiState = ResultadoFolioUiState(
        folioMuestraTexto = folioMuestra,
        aguaTexto = agua.formateadoConEscala(ESCALA_LABORATORIO),
        proteinaTexto = proteina.formateadoConEscala(ESCALA_LABORATORIO),
        lactosaTexto = lactosa.formateadoConEscala(ESCALA_LABORATORIO),
        densidadTexto = densidad.formateadoConEscala(ESCALA_LABORATORIO),
        temperaturaTexto = temperatura.formateadoConEscala(ESCALA_LABORATORIO),
        phTexto = ph.formateadoConEscala(ESCALA_LABORATORIO),
        aguaAnadidaTexto = if (aguaAnadida) "Sí" else "No",
        resultadoTexto = resultado?.name,
    )
}
