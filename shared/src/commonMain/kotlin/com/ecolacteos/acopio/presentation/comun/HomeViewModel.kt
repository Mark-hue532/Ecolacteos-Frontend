package com.ecolacteos.acopio.presentation.comun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.data.remote.dto.Rol
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarResumenSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ResumenSync
import com.ecolacteos.acopio.domain.usecase.SincronizarAhoraUseCase
import com.ecolacteos.acopio.presentation.formateada
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** `S-03 · Home` (`MOBILE_SCREENS.md §4`) -- `UiState` literal del documento, con los campos ya formateados. */
data class HomeUiState(
    val nombre: String = "",
    val rol: Rol = Rol.UNKNOWN,
    val etiquetaAccionPrincipal: String = "",
    /** `null` -> sin acceso secundario para este rol (ej. CALIDAD/PRODUCCION/RECEPCION todavía, Fase 8C-8E). */
    val etiquetaAccesoSecundario: String? = null,
    val accionPrincipalDisponible: Boolean = false,
    val resumenSync: ResumenSync = ResumenSync(),
    val ultimoSyncTexto: String? = null,
    val comunicadosNoLeidos: Int = 0,
    val hayConexion: Boolean = true,
    val catalogosVacios: Boolean = false,
    val datosDesactualizados: Boolean = false,
)

sealed interface HomeEvent {
    data object AccionPrincipalPresionada : HomeEvent
    data object AccesoSecundarioPresionado : HomeEvent
    data object EstadoSyncPresionado : HomeEvent
    data object SincronizarPresionado : HomeEvent
}

sealed interface HomeEffect {
    data object NavegarARegistrarVenta : HomeEffect
    /** Acceso secundario del rol (`§4`: "Accesos secundarios del rol") -- para VENTAS, ver el día. */
    data object NavegarAHomeVentas : HomeEffect
    data object NavegarAEstadoSincronizacion : HomeEffect

    // Fase 8A -- ACOPIADOR (MOBILE_SCREENS.md §5).
    data object NavegarARutaAcopio : HomeEffect
    data object NavegarAEscanearQrAcopio : HomeEffect
}

private const val VENTANA_DESACTUALIZADO_HORAS = 24L

/**
 * VENTAS (Fase 7) y ACOPIADOR (Fase 8A) tienen acción principal propia -- para cualquier otro rol,
 * [HomeUiState.accionPrincipalDisponible] queda en `false` y la UI lo muestra sin acción (nunca una
 * pantalla a medias, trampa #1 de `PROMPT_FASE_07.md §3`).
 */
class HomeViewModel(
    private val gestorSesion: GestorSesion,
    private val observarResumenSyncUseCase: ObservarResumenSyncUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
    private val sincronizarAhoraUseCase: SincronizarAhoraUseCase,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effect = Channel<HomeEffect>(Channel.BUFFERED)
    val effect: Flow<HomeEffect> = _effect.receiveAsFlow()

    init {
        val sesion = gestorSesion.sesion.value
        aplicarSesion(sesion?.nombre.orEmpty(), sesion?.rol ?: Rol.UNKNOWN)

        combine(observarResumenSyncUseCase(), observarConectividadUseCase()) { resumen, conectado -> resumen to conectado }
            .onEach { (resumen, conectado) ->
                _uiState.value = _uiState.value.copy(
                    resumenSync = resumen,
                    ultimoSyncTexto = resumen.ultimoSyncOk?.formateada(),
                    hayConexion = conectado,
                    catalogosVacios = resumen.catalogosVacios,
                    datosDesactualizados = resumen.ultimoSyncOk?.let { estaDesactualizado(it) } ?: false,
                )
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(evento: HomeEvent) {
        when (evento) {
            HomeEvent.AccionPrincipalPresionada -> emitirSiHabilitado(
                when (_uiState.value.rol) {
                    Rol.VENTAS -> HomeEffect.NavegarARegistrarVenta
                    Rol.ACOPIADOR -> HomeEffect.NavegarARutaAcopio
                    else -> null
                },
            )
            HomeEvent.AccesoSecundarioPresionado -> emitirSiHabilitado(
                when (_uiState.value.rol) {
                    Rol.VENTAS -> HomeEffect.NavegarAHomeVentas
                    Rol.ACOPIADOR -> HomeEffect.NavegarAEscanearQrAcopio
                    else -> null
                },
            )
            HomeEvent.EstadoSyncPresionado -> viewModelScope.launch { _effect.send(HomeEffect.NavegarAEstadoSincronizacion) }
            HomeEvent.SincronizarPresionado -> viewModelScope.launch { sincronizarAhoraUseCase() }
        }
    }

    private fun emitirSiHabilitado(efecto: HomeEffect?) {
        if (efecto != null && _uiState.value.accionPrincipalDisponible) {
            viewModelScope.launch { _effect.send(efecto) }
        }
    }

    private fun estaDesactualizado(ultimoSyncOk: LocalDateTime): Boolean {
        val ahora = reloj.now()
        val ultimo = ultimoSyncOk.toInstant(zona)
        return (ahora - ultimo).inWholeHours >= VENTANA_DESACTUALIZADO_HORAS
    }

    private fun aplicarSesion(nombre: String, rol: Rol) {
        _uiState.value = _uiState.value.copy(
            nombre = nombre,
            rol = rol,
            etiquetaAccionPrincipal = when (rol) {
                Rol.VENTAS -> "Registrar venta"
                Rol.ACOPIADOR -> "Ver mi ruta"
                else -> ""
            },
            etiquetaAccesoSecundario = when (rol) {
                Rol.VENTAS -> "Ver ventas del día"
                Rol.ACOPIADOR -> "Escanear QR"
                else -> null
            },
            accionPrincipalDisponible = rol == Rol.VENTAS || rol == Rol.ACOPIADOR,
        )
    }
}
