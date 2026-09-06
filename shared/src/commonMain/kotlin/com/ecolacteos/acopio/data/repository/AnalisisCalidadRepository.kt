package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.core.generarUuidV4
import com.ecolacteos.acopio.data.local.datasource.AnalisisCalidadLocalDataSource
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.model.AnalisisCalidad
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.synchronization.SyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** Datos que la UI ya validó para crear un `AnalisisCalidad` (`§4.2`) -- [referenciaPadre] ya resuelta por la UI (propio o ajeno elegido de una lista). */
data class NuevoAnalisisCalidad(
    val referenciaPadre: ReferenciaRegistroAcopio,
    val folioMuestra: String,
    val agua: Decimal?,
    val proteina: Decimal?,
    val lactosa: Decimal?,
    val densidad: Decimal?,
    val temperatura: Decimal?,
    val ph: Decimal?,
    val aguaAnadida: Boolean,
)

/** `AnalisisCalidad` (`PROMPT_FASE_06.md §4.2`) -- con dependencia de un `RegistroAcopio` padre. */
interface AnalisisCalidadRepository {
    /** Ver [ResultadoCrearHijo] -- único caso que puede rechazar la creación es un padre ajeno no resoluble sin red. */
    suspend fun crear(datos: NuevoAnalisisCalidad): ResultadoCrearHijo
    fun observarPendientes(): Flow<List<AnalisisCalidad>>
    fun reintentar(uuidCliente: String)
    suspend fun purgarSincronizados()
}

internal class AnalisisCalidadRepositoryImpl(
    private val gestorSesion: GestorSesion,
    private val local: AnalisisCalidadLocalDataSource,
    private val resolutor: ResolutorPadreRegistroAcopio,
    private val syncEngine: SyncEngine,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : AnalisisCalidadRepository {

    override suspend fun crear(datos: NuevoAnalisisCalidad): ResultadoCrearHijo {
        val usuarioId = gestorSesion.sesionActual()?.usuarioId
            ?: error("crear() llamado sin sesión activa -- la UI no debería permitir capturar sin login")

        val resolucion = resolutor.resolver(datos.referenciaPadre)
        if (resolucion is ResolucionPadre.SinConectividadParaResolver) {
            return ResultadoCrearHijo.PadreAjenoNoResolubleSinConectividad
        }
        resolucion as ResolucionPadre.Registrable

        val uuidCliente = generarUuidV4()
        // PENDING solo si el padre ya tiene id de servidor resuelto; si no, PENDING_DEPENDENCY -- el Sync
        // Engine lo promueve solo cuando el padre sincronice (mecanismo 1, §18.1).
        val syncStatus = if (resolucion.registroAcopioServerId != null) SyncStatus.PENDING else SyncStatus.PENDING_DEPENDENCY

        local.insertar(
            AnalisisCalidad(
                uuidCliente = uuidCliente,
                serverId = null,
                usuarioId = usuarioId,
                registroAcopioUuidCliente = resolucion.registroAcopioUuidCliente,
                registroAcopioServerId = resolucion.registroAcopioServerId,
                folioMuestra = datos.folioMuestra,
                agua = datos.agua,
                proteina = datos.proteina,
                lactosa = datos.lactosa,
                densidad = datos.densidad,
                temperatura = datos.temperatura,
                ph = datos.ph,
                aguaAnadida = datos.aguaAnadida,
                syncStatus = syncStatus,
                syncAttempts = 0,
                syncError = null,
                nextAttemptAt = null,
                creadoEn = ahoraComoFechaHora(reloj, zona),
                sincronizadoEn = null,
            ),
        )
        syncEngine.solicitarSyncOportunista()
        return ResultadoCrearHijo.Creado(uuidCliente)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarPendientes(): Flow<List<AnalisisCalidad>> =
        gestorSesion.sesion.flatMapLatest { sesion ->
            val usuarioId = sesion?.usuarioId ?: return@flatMapLatest flowOf(emptyList())
            local.observarTodos(usuarioId)
        }

    override fun reintentar(uuidCliente: String) {
        local.actualizarEstadoSync(uuidCliente, SyncStatus.PENDING, syncAttempts = 0, syncError = null, nextAttemptAt = null)
        syncEngine.solicitarSyncOportunista()
    }

    override suspend fun purgarSincronizados() {
        val usuarioId = gestorSesion.sesionActual()?.usuarioId ?: return
        local.eliminarSincronizadosDeUsuario(usuarioId)
    }
}
