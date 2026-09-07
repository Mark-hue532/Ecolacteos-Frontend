package com.ecolacteos.acopio.presentation.calidad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.decimalDesdeTexto
import com.ecolacteos.acopio.data.repository.NuevoAnalisisCalidad
import com.ecolacteos.acopio.data.repository.ReferenciaRegistroAcopio
import com.ecolacteos.acopio.data.repository.ResultadoCrearHijo
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearAnalisisCalidadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.network.jsonApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

private const val PANTALLA_BORRADOR = "registrar_analisis_calidad"
private const val DEBOUNCE_BORRADOR_MS = 500L
private const val MAX_FOLIO_MUESTRA = 40

/**
 * `RegistrarAnalisisUiState` (`MOBILE_SCREENS.md §6`, `C-03`). Los 6 parámetros de laboratorio viajan como
 * texto crudo (`§3.3`): vacío significa `null`, nunca `0` (`§10.1` regla 3, trampa #8). **Sin campo
 * `resultado`** -- lo calcula el servidor, no se muestra un valor "previsto" acá (trampa #9).
 */
data class RegistrarAnalisisUiState(
    val folioMuestra: String = "",
    val agua: String = "",
    val proteina: String = "",
    val lactosa: String = "",
    val densidad: String = "",
    val temperatura: String = "",
    val ph: String = "",
    val aguaAnadida: Boolean = false,
    val errorFolio: String? = null,
    val errorAgua: String? = null,
    val errorProteina: String? = null,
    val errorLactosa: String? = null,
    val errorDensidad: String? = null,
    val errorTemperatura: String? = null,
    val errorPh: String? = null,
    val guardando: Boolean = false,
    val hayConexion: Boolean = true,
    val hayBorradorParaRetomar: Boolean = false,
    val mensajePadreNoResoluble: String? = null,
) {
    val puedeGuardar: Boolean
        get() = !guardando && folioMuestra.isNotBlank() &&
            listOf(errorFolio, errorAgua, errorProteina, errorLactosa, errorDensidad, errorTemperatura, errorPh).all { it == null }
}

sealed interface RegistrarAnalisisEvent {
    data class FolioCambio(val texto: String) : RegistrarAnalisisEvent
    data class AguaCambio(val texto: String) : RegistrarAnalisisEvent
    data class ProteinaCambio(val texto: String) : RegistrarAnalisisEvent
    data class LactosaCambio(val texto: String) : RegistrarAnalisisEvent
    data class DensidadCambio(val texto: String) : RegistrarAnalisisEvent
    data class TemperaturaCambio(val texto: String) : RegistrarAnalisisEvent
    data class PhCambio(val texto: String) : RegistrarAnalisisEvent
    data class AguaAnadidaCambio(val valor: Boolean) : RegistrarAnalisisEvent
    data object GuardarPresionado : RegistrarAnalisisEvent
    data object RetomarBorradorPresionado : RegistrarAnalisisEvent
    data object DescartarBorradorPresionado : RegistrarAnalisisEvent
}

sealed interface RegistrarAnalisisEffect {
    /** `§2.1` regla 3: vuelve atrás con `Snackbar`. */
    data object GuardadoConExito : RegistrarAnalisisEffect
}

@Serializable
private data class BorradorAnalisis(
    val folioMuestra: String,
    val agua: String,
    val proteina: String,
    val lactosa: String,
    val densidad: String,
    val temperatura: String,
    val ph: String,
    val aguaAnadida: Boolean,
)

/**
 * `C-03 · Registrar análisis de calidad ★` (Fase 8C, `MOBILE_SCREENS.md §6`) -- OFFLINE-FIRST con
 * dependencia (`§18.1`). [referenciaPadre] viene resuelta de `C-02` (`§0` del prompt: "no reimplementa
 * nada de eso"), no editable en esta pantalla. Hermana de `RegistrarAcopioViewModel` (`A-04`, `8A`): mismo
 * patrón de borrador, sin GPS ni permisos porque esta captura no los necesita.
 */
class RegistrarAnalisisViewModel(
    registroAcopioUuidCliente: String? = null,
    registroAcopioServerId: String? = null,
    private val crearAnalisisCalidadUseCase: CrearAnalisisCalidadUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
    private val borradorFormularioUseCase: BorradorFormularioUseCase,
) : ViewModel() {

    private val referenciaPadre: ReferenciaRegistroAcopio = when {
        registroAcopioUuidCliente != null -> ReferenciaRegistroAcopio.Propio(registroAcopioUuidCliente)
        registroAcopioServerId != null -> ReferenciaRegistroAcopio.Ajeno(registroAcopioServerId)
        else -> error("C-03 requiere exactamente una referencia de padre (uuidCliente o serverId) -- viene de C-02")
    }

    private val _uiState = MutableStateFlow(RegistrarAnalisisUiState())
    val uiState: StateFlow<RegistrarAnalisisUiState> = _uiState.asStateFlow()

    private val _effect = Channel<RegistrarAnalisisEffect>(Channel.BUFFERED)
    val effect: Flow<RegistrarAnalisisEffect> = _effect.receiveAsFlow()

    private var borradorPendiente: BorradorAnalisis? = null
    private var jobDebounceBorrador: Job? = null

    init {
        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)

        val payload = borradorFormularioUseCase.obtener(PANTALLA_BORRADOR)
        if (payload != null) {
            borradorPendiente = payload.aBorradorOrNull()
            if (borradorPendiente != null) _uiState.update { it.copy(hayBorradorParaRetomar = true) }
        }
    }

    fun onEvent(evento: RegistrarAnalisisEvent) {
        when (evento) {
            is RegistrarAnalisisEvent.FolioCambio -> {
                _uiState.update { it.copy(folioMuestra = evento.texto, errorFolio = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarAnalisisEvent.AguaCambio -> {
                _uiState.update { it.copy(agua = evento.texto, errorAgua = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarAnalisisEvent.ProteinaCambio -> {
                _uiState.update { it.copy(proteina = evento.texto, errorProteina = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarAnalisisEvent.LactosaCambio -> {
                _uiState.update { it.copy(lactosa = evento.texto, errorLactosa = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarAnalisisEvent.DensidadCambio -> {
                _uiState.update { it.copy(densidad = evento.texto, errorDensidad = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarAnalisisEvent.TemperaturaCambio -> {
                _uiState.update { it.copy(temperatura = evento.texto, errorTemperatura = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarAnalisisEvent.PhCambio -> {
                _uiState.update { it.copy(ph = evento.texto, errorPh = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarAnalisisEvent.AguaAnadidaCambio -> {
                _uiState.update { it.copy(aguaAnadida = evento.valor) }
                programarGuardadoDeBorrador()
            }
            RegistrarAnalisisEvent.GuardarPresionado -> guardar()
            RegistrarAnalisisEvent.RetomarBorradorPresionado -> retomarBorrador()
            RegistrarAnalisisEvent.DescartarBorradorPresionado -> {
                borradorFormularioUseCase.descartar(PANTALLA_BORRADOR)
                borradorPendiente = null
                _uiState.update { it.copy(hayBorradorParaRetomar = false) }
            }
        }
    }

    private fun retomarBorrador() {
        val borrador = borradorPendiente ?: return
        _uiState.update {
            it.copy(
                folioMuestra = borrador.folioMuestra,
                agua = borrador.agua,
                proteina = borrador.proteina,
                lactosa = borrador.lactosa,
                densidad = borrador.densidad,
                temperatura = borrador.temperatura,
                ph = borrador.ph,
                aguaAnadida = borrador.aguaAnadida,
                hayBorradorParaRetomar = false,
            )
        }
        borradorPendiente = null
    }

    private fun programarGuardadoDeBorrador() {
        jobDebounceBorrador?.cancel()
        jobDebounceBorrador = viewModelScope.launch {
            delay(DEBOUNCE_BORRADOR_MS)
            val estado = _uiState.value
            val borrador = BorradorAnalisis(
                folioMuestra = estado.folioMuestra,
                agua = estado.agua,
                proteina = estado.proteina,
                lactosa = estado.lactosa,
                densidad = estado.densidad,
                temperatura = estado.temperatura,
                ph = estado.ph,
                aguaAnadida = estado.aguaAnadida,
            )
            borradorFormularioUseCase.guardar(PANTALLA_BORRADOR, jsonApi.encodeToString(BorradorAnalisis.serializer(), borrador))
        }
    }

    private fun guardar() {
        val estado = _uiState.value

        val errorFolio = when {
            estado.folioMuestra.isBlank() -> "Ingresá el folio de la muestra"
            estado.folioMuestra.length > MAX_FOLIO_MUESTRA -> "Máximo $MAX_FOLIO_MUESTRA caracteres"
            else -> null
        }
        val agua = estado.agua.aDecimalOpcional()
        val proteina = estado.proteina.aDecimalOpcional()
        val lactosa = estado.lactosa.aDecimalOpcional()
        val densidad = estado.densidad.aDecimalOpcional()
        val temperatura = estado.temperatura.aDecimalOpcional()
        val ph = estado.ph.aDecimalOpcional()

        val hayErrores = listOf(errorFolio, agua.error, proteina.error, lactosa.error, densidad.error, temperatura.error, ph.error).any { it != null }
        if (hayErrores) {
            _uiState.update {
                it.copy(
                    errorFolio = errorFolio,
                    errorAgua = agua.error,
                    errorProteina = proteina.error,
                    errorLactosa = lactosa.error,
                    errorDensidad = densidad.error,
                    errorTemperatura = temperatura.error,
                    errorPh = ph.error,
                )
            }
            return
        }

        _uiState.update { it.copy(guardando = true) }
        val datos = NuevoAnalisisCalidad(
            referenciaPadre = referenciaPadre,
            folioMuestra = estado.folioMuestra,
            agua = agua.valor,
            proteina = proteina.valor,
            lactosa = lactosa.valor,
            densidad = densidad.valor,
            temperatura = temperatura.valor,
            ph = ph.valor,
            aguaAnadida = estado.aguaAnadida,
        )

        viewModelScope.launch {
            when (crearAnalisisCalidadUseCase(datos)) {
                is ResultadoCrearHijo.Creado -> {
                    borradorFormularioUseCase.descartar(PANTALLA_BORRADOR)
                    _uiState.update { it.copy(guardando = false) }
                    _effect.send(RegistrarAnalisisEffect.GuardadoConExito)
                }
                // Defensivo -- no debería ocurrir viniendo de C-02 (un Ajeno de esa lista ya está cacheado,
                // ver ClasificarPadresRegistroAcopioUseCase), pero el resolutor es genérico (§0 del prompt).
                ResultadoCrearHijo.PadreAjenoNoResolubleSinConectividad -> _uiState.update {
                    it.copy(guardando = false, mensajePadreNoResoluble = "No pudimos confirmar esa entrega sin conexión. Probá de nuevo con señal.")
                }
            }
        }
    }
}

private data class CampoOpcional(val valor: Decimal?, val error: String?)

private fun String.aDecimalOpcional(): CampoOpcional = if (isBlank()) {
    CampoOpcional(null, null)
} else {
    try {
        CampoOpcional(decimalDesdeTexto(this), null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CampoOpcional(null, "Ingresá un número válido")
    }
}

private fun String.aBorradorOrNull(): BorradorAnalisis? = try {
    jsonApi.decodeFromString(BorradorAnalisis.serializer(), this)
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}
