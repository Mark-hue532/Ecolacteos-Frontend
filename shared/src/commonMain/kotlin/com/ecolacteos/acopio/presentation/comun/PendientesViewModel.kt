package com.ecolacteos.acopio.presentation.comun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.model.AnalisisCalidad
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.model.LoteProduccion
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.Venta
import com.ecolacteos.acopio.domain.usecase.ConEstado
import com.ecolacteos.acopio.domain.usecase.DescartarPendienteUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarEstadoSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarPendientesUseCase
import com.ecolacteos.acopio.domain.usecase.ReintentarManualUseCase
import com.ecolacteos.acopio.domain.usecase.ResumenPendientes
import com.ecolacteos.acopio.domain.usecase.SincronizarAhoraUseCase
import com.ecolacteos.acopio.presentation.formateada
import com.ecolacteos.acopio.synchronization.EstadoSync
import com.ecolacteos.acopio.synchronization.RecursoSync
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

private const val ESCALA_LITROS = 2
private const val DIAS_AVISO_ESPERA = 3

/** `S-05 · Pendientes` (`MOBILE_SCREENS.md §4`) -- literal del documento. */
data class ItemPendiente(
    val uuidCliente: String,
    val recurso: RecursoSync,
    val resumen: String,
    val estado: EstadoSincronizacion,
    val motivoError: String? = null,
    val intentos: Int = 0,
    val diasEsperando: Int = 0,
) {
    /** `A-04`/`V-02` son las únicas pantallas de captura que existen hoy (`8A`/Fase 7) -- ver checkpoint. */
    val puedeEditar: Boolean get() = recurso == RecursoSync.REGISTRO_ACOPIO || recurso == RecursoSync.VENTA
}

data class PendientesUiState(
    val conError: List<ItemPendiente> = emptyList(),
    val esperandoDependencia: List<ItemPendiente> = emptyList(),
    val porEnviar: List<ItemPendiente> = emptyList(),
    val hayConexion: Boolean = true,
    val sincronizando: Boolean = false,
    val cargando: Boolean = true,
    /** `null` = sin diálogo abierto. Nombra explícitamente lo que se pierde (`§13`, `S-05` regla 3). */
    val pendienteADescartar: ItemPendiente? = null,
) {
    val vacio: Boolean get() = !cargando && conError.isEmpty() && esperandoDependencia.isEmpty() && porEnviar.isEmpty()
}

sealed interface PendientesEvent {
    data class ReintentarPresionado(val item: ItemPendiente) : PendientesEvent
    data class EditarPresionado(val item: ItemPendiente) : PendientesEvent
    data class DescartarPresionado(val item: ItemPendiente) : PendientesEvent
    data object ConfirmarDescartePresionado : PendientesEvent
    data object CancelarDescartePresionado : PendientesEvent
    data object SincronizarAhoraPresionado : PendientesEvent
}

sealed interface PendientesEffect {
    data class NavegarAEditarAcopio(val uuidCliente: String) : PendientesEffect
    data class NavegarAEditarVenta(val uuidCliente: String) : PendientesEffect
}

/**
 * `S-05` (Fase 8B) -- la pantalla más importante del modo offline (`§0` del prompt). Maneja los **cuatro**
 * recursos desde el día uno (trampa #1: `CALIDAD`/`LOTE` no tienen pantalla de captura todavía, pero sí
 * pueden aparecer acá si alguna vez existen filas -- `8C`/`8D` no van a tener que volver a abrir esto).
 */
class PendientesViewModel(
    private val observarPendientesUseCase: ObservarPendientesUseCase,
    private val observarCatalogosUseCase: ObservarCatalogosUseCase,
    private val observarConectividadUseCase: ObservarConectividadUseCase,
    private val observarEstadoSyncUseCase: ObservarEstadoSyncUseCase,
    private val reintentarManualUseCase: ReintentarManualUseCase,
    private val descartarPendienteUseCase: DescartarPendienteUseCase,
    private val sincronizarAhoraUseCase: SincronizarAhoraUseCase,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendientesUiState())
    val uiState: StateFlow<PendientesUiState> = _uiState.asStateFlow()

    private val _effect = Channel<PendientesEffect>(Channel.BUFFERED)
    val effect: Flow<PendientesEffect> = _effect.receiveAsFlow()

    init {
        combine(
            observarPendientesUseCase(),
            observarCatalogosUseCase.proveedores(),
            observarCatalogosUseCase.tiposQueso(),
            observarConectividadUseCase(),
            observarEstadoSyncUseCase(),
        ) { resumen, proveedores, tiposQueso, conectado, estadoMotor ->
            val proveedorNombrePorId = proveedores.associate { it.id to it.nombre }
            val tipoQuesoNombrePorId = tiposQueso.associate { it.id to it.nombre }
            aplicar(resumen, proveedorNombrePorId, tipoQuesoNombrePorId, conectado, estadoMotor == EstadoSync.SINCRONIZANDO)
        }.onEach { nuevoEstado -> _uiState.update { nuevoEstado.copy(pendienteADescartar = it.pendienteADescartar) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(evento: PendientesEvent) {
        when (evento) {
            is PendientesEvent.ReintentarPresionado -> reintentarManualUseCase(evento.item.recurso, evento.item.uuidCliente)
            is PendientesEvent.EditarPresionado -> viewModelScope.launch {
                val efecto = when (evento.item.recurso) {
                    RecursoSync.REGISTRO_ACOPIO -> PendientesEffect.NavegarAEditarAcopio(evento.item.uuidCliente)
                    RecursoSync.VENTA -> PendientesEffect.NavegarAEditarVenta(evento.item.uuidCliente)
                    RecursoSync.ANALISIS_CALIDAD, RecursoSync.LOTE_PRODUCCION -> return@launch // sin pantalla de edición todavía (8C/8D)
                }
                _effect.send(efecto)
            }
            is PendientesEvent.DescartarPresionado -> _uiState.update { it.copy(pendienteADescartar = evento.item) }
            PendientesEvent.CancelarDescartePresionado -> _uiState.update { it.copy(pendienteADescartar = null) }
            PendientesEvent.ConfirmarDescartePresionado -> confirmarDescarte()
            PendientesEvent.SincronizarAhoraPresionado -> viewModelScope.launch { sincronizarAhoraUseCase() }
        }
    }

    private fun confirmarDescarte() {
        val item = _uiState.value.pendienteADescartar ?: return
        viewModelScope.launch {
            descartarPendienteUseCase(item.recurso, item.uuidCliente)
            _uiState.update { it.copy(pendienteADescartar = null) }
        }
    }

    private fun aplicar(
        resumen: ResumenPendientes,
        proveedorNombrePorId: Map<String, String>,
        tipoQuesoNombrePorId: Map<String, String>,
        conectado: Boolean,
        sincronizando: Boolean,
    ): PendientesUiState {
        val ahora = reloj.now()

        val items = resumen.registros.map { it.aItemPendienteAcopio(proveedorNombrePorId, ahora) } +
            resumen.analisis.map { it.aItemPendienteCalidad(ahora) } +
            resumen.lotes.map { it.aItemPendienteLote(tipoQuesoNombrePorId, ahora) } +
            resumen.ventas.map { it.aItemPendienteVenta(tipoQuesoNombrePorId, ahora) }

        return PendientesUiState(
            conError = items.filter { it.estado is EstadoSincronizacion.Fallido },
            esperandoDependencia = items.filter { it.estado is EstadoSincronizacion.EsperandoDependencia },
            porEnviar = items.filter { it.estado is EstadoSincronizacion.Pendiente || it.estado is EstadoSincronizacion.Sincronizando },
            hayConexion = conectado,
            sincronizando = sincronizando,
            cargando = false,
        )
    }

    // Nombres distintos por recurso (no sobrecarga) -- 3 de estas 4 extensiones comparten la misma firma de
    // valor `(Map<String, String>, Instant): ItemPendiente` y el tipo del receiver se borra en la JVM
    // (erasure): sobrecargarlas produce "Platform declaration clash" en `compileKotlinJvm`.
    private fun ConEstado<RegistroAcopio>.aItemPendienteAcopio(proveedorNombrePorId: Map<String, String>, ahora: Instant): ItemPendiente {
        val proveedorNombre = proveedorNombrePorId[dato.proveedorId] ?: dato.proveedorId
        return ItemPendiente(
            uuidCliente = dato.uuidCliente,
            recurso = RecursoSync.REGISTRO_ACOPIO,
            resumen = "${dato.litros.aTextoConEscala(ESCALA_LITROS)} L — $proveedorNombre — ${dato.fechaHora.formateada()}",
            estado = estado,
            motivoError = (estado as? EstadoSincronizacion.Fallido)?.motivo,
            intentos = dato.syncAttempts,
            diasEsperando = diasEsperando(dato.creadoEn, ahora),
        )
    }

    private fun ConEstado<AnalisisCalidad>.aItemPendienteCalidad(ahora: Instant): ItemPendiente = ItemPendiente(
        uuidCliente = dato.uuidCliente,
        recurso = RecursoSync.ANALISIS_CALIDAD,
        resumen = "Folio ${dato.folioMuestra} — ${dato.creadoEn.formateada()}",
        estado = estado,
        motivoError = (estado as? EstadoSincronizacion.Fallido)?.motivo,
        intentos = dato.syncAttempts,
        diasEsperando = diasEsperando(dato.creadoEn, ahora),
    )

    private fun ConEstado<LoteProduccion>.aItemPendienteLote(tipoQuesoNombrePorId: Map<String, String>, ahora: Instant): ItemPendiente {
        val tipoQuesoNombre = tipoQuesoNombrePorId[dato.tipoQuesoId] ?: dato.tipoQuesoId
        return ItemPendiente(
            uuidCliente = dato.uuidCliente,
            recurso = RecursoSync.LOTE_PRODUCCION,
            resumen = "${dato.litrosUsados.aTextoConEscala(ESCALA_LITROS)} L — $tipoQuesoNombre — ${dato.creadoEn.formateada()}",
            estado = estado,
            motivoError = (estado as? EstadoSincronizacion.Fallido)?.motivo,
            intentos = dato.syncAttempts,
            diasEsperando = diasEsperando(dato.creadoEn, ahora),
        )
    }

    private fun ConEstado<Venta>.aItemPendienteVenta(tipoQuesoNombrePorId: Map<String, String>, ahora: Instant): ItemPendiente {
        val tipoQuesoNombre = tipoQuesoNombrePorId[dato.tipoQuesoId] ?: dato.tipoQuesoId
        return ItemPendiente(
            uuidCliente = dato.uuidCliente,
            recurso = RecursoSync.VENTA,
            resumen = "${dato.cantidad} x $tipoQuesoNombre — ${dato.tipoCliente.name} — ${dato.fecha.formateada()}",
            estado = estado,
            motivoError = (estado as? EstadoSincronizacion.Fallido)?.motivo,
            intentos = dato.syncAttempts,
            diasEsperando = diasEsperando(dato.creadoEn, ahora),
        )
    }

    /** `diasEsperando` (`§4`): siempre `creadoEn` (dispositivo) contra el reloj del dispositivo -- nunca un marco de servidor (`§10.3`). */
    private fun diasEsperando(creadoEn: LocalDateTime, ahora: Instant): Int =
        (ahora - creadoEn.toInstant(zona)).inWholeDays.toInt().coerceAtLeast(0)
}

/** `§4` regla: si lleva más de [DIAS_AVISO_ESPERA] días, se marca como advertencia. */
val ItemPendiente.esperaConAdvertencia: Boolean get() = diasEsperando > DIAS_AVISO_ESPERA
