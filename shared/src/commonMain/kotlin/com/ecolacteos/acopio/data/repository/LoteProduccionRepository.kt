package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.core.generarUuidV4
import com.ecolacteos.acopio.data.local.datasource.LoteProduccionLocalDataSource
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.model.LoteProduccion
import com.ecolacteos.acopio.domain.model.LoteProduccionRegistro
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.synchronization.SyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** Datos que la UI ya validó para crear un `LoteProduccion` (`§4.2`) -- [referenciasRegistros] ya resueltas por la UI, una por registro consumido. */
data class NuevoLoteProduccion(
    val fecha: LocalDate,
    val tipoQuesoId: String,
    val litrosUsados: Decimal,
    val unidadesObtenidas: Int,
    val referenciasRegistros: List<ReferenciaRegistroAcopio>,
)

/** `LoteProduccion` (`PROMPT_FASE_06.md §4.2`) -- misma dependencia que `AnalisisCalidad`, pero sobre una LISTA de padres. */
interface LoteProduccionRepository {
    suspend fun crear(datos: NuevoLoteProduccion): ResultadoCrearHijo
    fun observarPendientes(): Flow<List<LoteProduccion>>
    fun reintentar(uuidCliente: String)
    suspend fun purgarSincronizados()
}

internal class LoteProduccionRepositoryImpl(
    private val gestorSesion: GestorSesion,
    private val local: LoteProduccionLocalDataSource,
    private val resolutor: ResolutorPadreRegistroAcopio,
    private val syncEngine: SyncEngine,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : LoteProduccionRepository {

    override suspend fun crear(datos: NuevoLoteProduccion): ResultadoCrearHijo {
        val usuarioId = gestorSesion.sesionActual()?.usuarioId
            ?: error("crear() llamado sin sesión activa -- la UI no debería permitir capturar sin login")

        // Un lote entra completo o no entra (§4.2): si CUALQUIERA de sus registros es un padre ajeno no
        // resoluble sin red, se rechaza la creación entera -- no se puede enviar registroAcopioIds a
        // medias sin cambiar en silencio lo que el lote significa (mismo criterio que Fase 5 aplicó al
        // resolver el envío, acá aplica a la creación).
        val resoluciones = mutableListOf<ResolucionPadre.Registrable>()
        for (referencia in datos.referenciasRegistros) {
            when (val resolucion = resolutor.resolver(referencia)) {
                is ResolucionPadre.Registrable -> resoluciones += resolucion
                ResolucionPadre.SinConectividadParaResolver -> return ResultadoCrearHijo.PadreAjenoNoResolubleSinConectividad
            }
        }

        val todosConIdDeServidor = resoluciones.isNotEmpty() && resoluciones.all { it.registroAcopioServerId != null }
        val syncStatus = if (todosConIdDeServidor) SyncStatus.PENDING else SyncStatus.PENDING_DEPENDENCY
        val uuidCliente = generarUuidV4()

        local.insertar(
            LoteProduccion(
                uuidCliente = uuidCliente,
                serverId = null,
                usuarioId = usuarioId,
                fecha = datos.fecha,
                tipoQuesoId = datos.tipoQuesoId,
                litrosUsados = datos.litrosUsados,
                unidadesObtenidas = datos.unidadesObtenidas,
                syncStatus = syncStatus,
                syncAttempts = 0,
                syncError = null,
                nextAttemptAt = null,
                creadoEn = ahoraComoFechaHora(reloj, zona),
                sincronizadoEn = null,
            ),
        )
        resoluciones.forEach { resolucion ->
            local.insertarRegistro(
                LoteProduccionRegistro(
                    loteUuidCliente = uuidCliente,
                    registroAcopioUuidCliente = resolucion.registroAcopioUuidCliente,
                    registroAcopioServerId = resolucion.registroAcopioServerId,
                ),
            )
        }
        syncEngine.solicitarSyncOportunista()
        return ResultadoCrearHijo.Creado(uuidCliente)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarPendientes(): Flow<List<LoteProduccion>> =
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
