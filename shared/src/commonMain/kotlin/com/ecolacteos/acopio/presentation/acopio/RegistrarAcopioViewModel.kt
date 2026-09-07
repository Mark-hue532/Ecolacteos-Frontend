package com.ecolacteos.acopio.presentation.acopio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.core.decimalDesdeTexto
import com.ecolacteos.acopio.data.repository.NuevoRegistroAcopio
import com.ecolacteos.acopio.domain.model.MotivoObservacion
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.model.Unidad
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.network.jsonApi
import com.ecolacteos.acopio.plataforma.EstadoPermiso
import com.ecolacteos.acopio.plataforma.GestorPermisos
import com.ecolacteos.acopio.plataforma.Permiso
import com.ecolacteos.acopio.plataforma.ProveedorUbicacion
import com.ecolacteos.acopio.plataforma.ResultadoUbicacion
import com.ecolacteos.acopio.presentation.formateada
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.time.Clock

private const val PANTALLA_BORRADOR = "registrar_acopio"
private const val DEBOUNCE_BORRADOR_MS = 500L
private const val TIMEOUT_GPS_MS = 15_000L
private const val ESCALA_LITROS = 2
private const val ESCALA_GPS = 6
private const val MAX_DIGITOS_ENTEROS_LITROS = 6
private const val VENTANA_AVISO_FECHA_PASADA_HORAS = 24L

/** `A-04`, `EstadoGps` literal de `MOBILE_SCREENS.md §5`. */
sealed interface EstadoGps {
    data object Buscando : EstadoGps
    data class Obtenido(val lat: Decimal, val lng: Decimal, val latTexto: String, val lngTexto: String) : EstadoGps
    data object NoDisponible : EstadoGps
    data object SinPermiso : EstadoGps
}

/**
 * `RegistrarAcopioUiState` (`MOBILE_SCREENS.md §5`), adaptado al patrón de `String`s ya formateados de
 * `§3.3`/`§10.1`. `fechaHoraTexto` se fija a "ahora" al abrir la pantalla y esta sub-fase no expone edición
 * en la UI -- mismo criterio que `V-02` con `fecha` (`PROMPT_FASE_07.md`, checkpoint: "no hay selector de
 * fecha entre los 10 componentes de `§13`"); el evento de cambio sí existe en el `ViewModel` (ver
 * [RegistrarAcopioEvent.FechaHoraCambio]) para que la validación sea testeable sin esperar a ese componente.
 */
data class RegistrarAcopioUiState(
    val proveedorId: String,
    val proveedor: Proveedor? = null,
    /** `false` = decisión #3 del checkpoint: el proveedor precargado ya no está en `proveedor_cache`. */
    val proveedorEnCache: Boolean = true,
    val unidades: List<Unidad> = emptyList(),
    val unidadSeleccionada: Unidad? = null,
    val fechaHora: LocalDateTime? = null,
    val fechaHoraTexto: String = "",
    val litrosTexto: String = "",
    val motivos: List<MotivoObservacion> = emptyList(),
    val motivoSeleccionado: MotivoObservacion? = null,
    val gps: EstadoGps = EstadoGps.Buscando,
    val errorLitros: String? = null,
    val errorFecha: String? = null,
    val avisoFechaPasada: String? = null,
    val errorUnidad: String? = null,
    val errorGeneral: String? = null,
    val guardando: Boolean = false,
    val hayConexion: Boolean = true,
    val hayBorradorParaRetomar: Boolean = false,
) {
    /** Nunca depende de [gps] (`§5`, regla 4: "nunca se impide guardar por falta de GPS"). */
    val puedeGuardar: Boolean
        get() = !guardando && proveedorEnCache && unidadSeleccionada != null && litrosTexto.isNotBlank() &&
            errorLitros == null && errorFecha == null
}

sealed interface RegistrarAcopioEvent {
    data class UnidadCambio(val valor: Unidad) : RegistrarAcopioEvent
    data class LitrosCambio(val texto: String) : RegistrarAcopioEvent
    data class MotivoCambio(val valor: MotivoObservacion?) : RegistrarAcopioEvent
    data class FechaHoraCambio(val valor: LocalDateTime) : RegistrarAcopioEvent
    data object GuardarPresionado : RegistrarAcopioEvent
    data object RetomarBorradorPresionado : RegistrarAcopioEvent
    data object DescartarBorradorPresionado : RegistrarAcopioEvent
    /** El `Screen` reporta acá el resultado real de `rememberSolicitanteDePermiso` (`§3` del prompt). */
    data class PermisoUbicacionResuelto(val estado: EstadoPermiso) : RegistrarAcopioEvent
    data object VerHistorialPresionado : RegistrarAcopioEvent
}

sealed interface RegistrarAcopioEffect {
    /** `§2.1` regla 3: vuelve atrás con `Snackbar` "Guardado -- se enviará cuando haya señal". */
    data object GuardadoConExito : RegistrarAcopioEffect

    /** El `Screen` dispara `rememberSolicitanteDePermiso(Permiso.UBICACION, ...)` al recibir esto. */
    data object SolicitarPermisoUbicacion : RegistrarAcopioEffect

    /** Aviso discreto, una sola vez (`§5` regla 3) -- nunca bloquea, nunca se repite. */
    data object AvisoGpsNoDisponible : RegistrarAcopioEffect

    data class NavegarAHistorial(val proveedorId: String) : RegistrarAcopioEffect
}

@Serializable
private data class BorradorAcopio(
    val unidadId: String?,
    val litrosTexto: String,
    val motivoObservacionId: String?,
)

/**
 * `A-04 · Registrar acopio ★` (`MOBILE_SCREENS.md §5`) -- la pantalla central del producto. Hermana mayor:
 * `presentation/ventas/RegistrarVentaViewModel.kt` (`V-02`). GPS y permiso de ubicación son las dos
 * capacidades nuevas de esta sub-fase; litros/fecha siguen el mismo patrón de validación que `V-02`.
 */
class RegistrarAcopioViewModel(
    proveedorId: String,
    private val crearRegistroAcopioUseCase: CrearRegistroAcopioUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
    private val borradorFormularioUseCase: BorradorFormularioUseCase,
    private val gestorPermisos: GestorPermisos,
    private val proveedorUbicacion: ProveedorUbicacion,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val ahora: LocalDateTime = ahoraComoFechaHora(reloj, zona)

    private val _uiState = MutableStateFlow(
        RegistrarAcopioUiState(proveedorId = proveedorId, fechaHora = ahora, fechaHoraTexto = ahora.formateada()),
    )
    val uiState: StateFlow<RegistrarAcopioUiState> = _uiState.asStateFlow()

    private val _effect = Channel<RegistrarAcopioEffect>(Channel.BUFFERED)
    val effect: Flow<RegistrarAcopioEffect> = _effect.receiveAsFlow()

    private var borradorPendiente: BorradorAcopio? = null
    private var jobDebounceBorrador: Job? = null
    private var avisoGpsYaMostrado = false

    init {
        observarCatalogosUseCase.proveedores()
            .onEach { proveedores ->
                val encontrado = proveedores.firstOrNull { it.id == proveedorId }
                _uiState.update {
                    it.copy(
                        proveedor = encontrado,
                        proveedorEnCache = encontrado != null,
                        errorGeneral = if (encontrado == null) {
                            "Este proveedor ya no está disponible sin conexión. Volvé a buscarlo."
                        } else {
                            it.errorGeneral
                        },
                    )
                }
            }
            .launchIn(viewModelScope)

        observarCatalogosUseCase.unidades()
            .onEach { unidades -> _uiState.update { it.copy(unidades = unidades) } }
            .launchIn(viewModelScope)

        observarCatalogosUseCase.motivosObservacion()
            .onEach { motivos -> _uiState.update { it.copy(motivos = motivos) } }
            .launchIn(viewModelScope)

        observarConectividadUseCase()
            .onEach { conectado -> _uiState.update { it.copy(hayConexion = conectado) } }
            .launchIn(viewModelScope)

        val payload = borradorFormularioUseCase.obtener(PANTALLA_BORRADOR)
        if (payload != null) {
            borradorPendiente = payload.aBorradorOrNull()
            if (borradorPendiente != null) _uiState.update { it.copy(hayBorradorParaRetomar = true) }
        }

        iniciarBusquedaDeGps()
    }

    fun onEvent(evento: RegistrarAcopioEvent) {
        when (evento) {
            is RegistrarAcopioEvent.UnidadCambio -> {
                _uiState.update { it.copy(unidadSeleccionada = evento.valor, errorUnidad = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarAcopioEvent.LitrosCambio -> {
                _uiState.update { it.copy(litrosTexto = evento.texto, errorLitros = null) }
                programarGuardadoDeBorrador()
            }
            is RegistrarAcopioEvent.MotivoCambio -> {
                _uiState.update { it.copy(motivoSeleccionado = evento.valor) }
                programarGuardadoDeBorrador()
            }
            is RegistrarAcopioEvent.FechaHoraCambio -> validarYAplicarFecha(evento.valor)
            RegistrarAcopioEvent.GuardarPresionado -> guardar()
            RegistrarAcopioEvent.RetomarBorradorPresionado -> retomarBorrador()
            RegistrarAcopioEvent.DescartarBorradorPresionado -> {
                borradorFormularioUseCase.descartar(PANTALLA_BORRADOR)
                borradorPendiente = null
                _uiState.update { it.copy(hayBorradorParaRetomar = false) }
            }
            is RegistrarAcopioEvent.PermisoUbicacionResuelto -> onPermisoUbicacionResuelto(evento.estado)
            RegistrarAcopioEvent.VerHistorialPresionado ->
                viewModelScope.launch { _effect.send(RegistrarAcopioEffect.NavegarAHistorial(_uiState.value.proveedorId)) }
        }
    }

    /**
     * `§5` reglas 1-2: se pide en paralelo al llenado, nunca bloquea, y si no hay fix en 15 s se marca
     * `NoDisponible` -- el registro se guarda igual. El permiso se resuelve primero: si no está concedido,
     * se emite el `Effect` que dispara `rememberSolicitanteDePermiso` en el `Screen` (`§3` del prompt).
     */
    private fun iniciarBusquedaDeGps() {
        if (!gestorPermisos.tieneConcedido(Permiso.UBICACION)) {
            viewModelScope.launch { _effect.send(RegistrarAcopioEffect.SolicitarPermisoUbicacion) }
            return
        }
        buscarUbicacion()
    }

    private fun onPermisoUbicacionResuelto(estado: EstadoPermiso) {
        if (estado == EstadoPermiso.CONCEDIDO) {
            buscarUbicacion()
        } else {
            aplicarResultadoGps(ResultadoUbicacion.SinPermiso)
        }
    }

    private fun buscarUbicacion() {
        viewModelScope.launch {
            val resultado = withTimeoutOrNull(TIMEOUT_GPS_MS) { proveedorUbicacion.obtenerUbicacionActual() }
            aplicarResultadoGps(resultado ?: ResultadoUbicacion.NoDisponible)
        }
    }

    private fun aplicarResultadoGps(resultado: ResultadoUbicacion) {
        val nuevoEstado = when (resultado) {
            is ResultadoUbicacion.Obtenida -> EstadoGps.Obtenido(
                lat = resultado.lat,
                lng = resultado.lng,
                latTexto = resultado.lat.aTextoConEscala(ESCALA_GPS),
                lngTexto = resultado.lng.aTextoConEscala(ESCALA_GPS),
            )
            ResultadoUbicacion.SinPermiso -> EstadoGps.SinPermiso
            ResultadoUbicacion.NoDisponible -> EstadoGps.NoDisponible
        }
        _uiState.update { it.copy(gps = nuevoEstado) }

        if (nuevoEstado !is EstadoGps.Obtenido && !avisoGpsYaMostrado) {
            avisoGpsYaMostrado = true
            viewModelScope.launch { _effect.send(RegistrarAcopioEffect.AvisoGpsNoDisponible) }
        }
    }

    private fun validarYAplicarFecha(nuevaFecha: LocalDateTime) {
        val ahoraActual = ahoraComoFechaHora(reloj, zona)
        val esFutura = nuevaFecha.toInstant(zona) > ahoraActual.toInstant(zona)
        if (esFutura) {
            _uiState.update { it.copy(fechaHora = nuevaFecha, fechaHoraTexto = nuevaFecha.formateada(), errorFecha = "La fecha no puede ser futura", avisoFechaPasada = null) }
            return
        }
        val horasEnElPasado = (ahoraActual.toInstant(zona) - nuevaFecha.toInstant(zona)).inWholeHours
        val aviso = if (horasEnElPasado >= VENTANA_AVISO_FECHA_PASADA_HORAS) {
            "Esta fecha es de hace más de 24 horas -- confirmá que es correcta"
        } else {
            null
        }
        _uiState.update {
            it.copy(fechaHora = nuevaFecha, fechaHoraTexto = nuevaFecha.formateada(), errorFecha = null, avisoFechaPasada = aviso)
        }
    }

    private fun retomarBorrador() {
        val borrador = borradorPendiente ?: return
        val estado = _uiState.value
        val unidad = estado.unidades.firstOrNull { it.id == borrador.unidadId }
        val motivo = estado.motivos.firstOrNull { it.id == borrador.motivoObservacionId }
        _uiState.update {
            it.copy(
                unidadSeleccionada = unidad,
                litrosTexto = borrador.litrosTexto,
                motivoSeleccionado = motivo,
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
            val borrador = BorradorAcopio(
                unidadId = estado.unidadSeleccionada?.id,
                litrosTexto = estado.litrosTexto,
                motivoObservacionId = estado.motivoSeleccionado?.id,
            )
            borradorFormularioUseCase.guardar(PANTALLA_BORRADOR, jsonApi.encodeToString(BorradorAcopio.serializer(), borrador))
        }
    }

    private fun guardar() {
        val estado = _uiState.value
        val litros = estado.litrosTexto.toDecimalOrNull()

        val errorLitros = when {
            litros == null -> "Ingresá una cantidad válida"
            litros.isNegative -> "Los litros no pueden ser negativos"
            digitosEnterosDe(litros) > MAX_DIGITOS_ENTEROS_LITROS -> "Máximo $MAX_DIGITOS_ENTEROS_LITROS dígitos enteros"
            else -> null
        }
        val errorUnidad = if (estado.unidadSeleccionada == null) "Seleccioná una unidad" else null

        if (errorLitros != null || errorUnidad != null || estado.errorFecha != null || !estado.proveedorEnCache) {
            _uiState.update { it.copy(errorLitros = errorLitros, errorUnidad = errorUnidad) }
            return
        }

        _uiState.update { it.copy(guardando = true, errorGeneral = null) }
        val gpsActual = estado.gps as? EstadoGps.Obtenido

        viewModelScope.launch {
            crearRegistroAcopioUseCase(
                NuevoRegistroAcopio(
                    proveedorId = estado.proveedorId,
                    unidadId = estado.unidadSeleccionada!!.id,
                    fechaHora = estado.fechaHora ?: ahoraComoFechaHora(reloj, zona),
                    litros = litros!!,
                    gpsLat = gpsActual?.lat,
                    gpsLng = gpsActual?.lng,
                    motivoObservacionId = estado.motivoSeleccionado?.id,
                    // Nunca hay captura por voz en v1 (MOBILE_SCREENS.md §16) -- siempre false, nunca null.
                    litrosPorVoz = false,
                ),
            )
            borradorFormularioUseCase.descartar(PANTALLA_BORRADOR)
            _uiState.update { it.copy(guardando = false) }
            _effect.send(RegistrarAcopioEffect.GuardadoConExito)
        }
    }
}

/** Cuenta dígitos enteros vía texto formateado -- nunca por magnitud numérica (evita pasar por `Double`). */
private fun digitosEnterosDe(valor: Decimal): Int {
    val texto = valor.aTextoConEscala(ESCALA_LITROS)
    val parteEntera = texto.substringBefore('.').removePrefix("-")
    return parteEntera.length
}

private fun String.toDecimalOrNull(): Decimal? = try {
    decimalDesdeTexto(this)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

private fun String.aBorradorOrNull(): BorradorAcopio? = try {
    jsonApi.decodeFromString(BorradorAcopio.serializer(), this)
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}
