package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.core.generarUuidV4
import com.ecolacteos.acopio.data.local.datasource.RegistroAcopioCacheLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RegistroAcopioLocalDataSource
import com.ecolacteos.acopio.data.remote.dto.RegistroAcopioResumenResponse
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.synchronization.SyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** Datos que la UI ya validó para crear un `RegistroAcopio` (`§4.1`) -- sin `uuidCliente`/`usuarioId`/estado: eso lo decide el Repository. */
data class NuevoRegistroAcopio(
    val proveedorId: String,
    val unidadId: String,
    val fechaHora: LocalDateTime,
    val litros: Decimal,
    val gpsLat: Decimal?,
    val gpsLng: Decimal?,
    val motivoObservacionId: String?,
    val litrosPorVoz: Boolean,
)

/**
 * Un ítem del historial combinado de un proveedor (`§16.4`): propio (con su ciclo de vida de sync
 * completo) o ajeno (de solo lectura, vía `registro_acopio_cache`). Deliberadamente **no** se fuerza el
 * lado propio al shape de [RegistroAcopioReferencia] -- son conceptualmente distintos (uno es mutable en
 * su estado de sync, el otro no tiene ninguno) y forzarlos al mismo tipo perdería esa distinción sin
 * necesidad real.
 */
sealed interface ItemHistorialRegistroAcopio {
    data class Propio(val registro: RegistroAcopio) : ItemHistorialRegistroAcopio
    data class Ajeno(val referencia: RegistroAcopioReferencia) : ItemHistorialRegistroAcopio
}

/**
 * `RegistroAcopio` (`PROMPT_FASE_06.md §4.1`) -- el más simple de los 4, sin dependencia de padre.
 */
interface RegistroAcopioRepository {
    /** Guarda local con `status=PENDING` y devuelve el `uuidCliente` generado. Nunca falla por red. */
    suspend fun crear(datos: NuevoRegistroAcopio): String

    /** Los `RegistroAcopio` de la sesión activa, para la futura pantalla `S-05` (`§16.4`). */
    fun observarPendientes(): Flow<List<RegistroAcopio>>

    /**
     * Historial de un proveedor puntual: propios (`registro_acopio_local`) + ajenos ya cacheados
     * (`registro_acopio_cache`), combinados reactivamente. Deduplicado *best-effort* contra
     * `RegistroAcopio.serverId` -- ver la nota de `DATA-013`×`DATA-014` en el checkpoint: con `DATA-014`
     * ese `serverId` casi nunca está poblado, así que esta deduplicación va a fallar en silencio la
     * mayoría de las veces. No es un bug nuevo de esta fase, es la mitigación de `DATA-013` perdiendo
     * eficacia por el hallazgo posterior -- no se reemplaza por un matching heurístico (trampa #3).
     */
    fun observarHistorialProveedor(proveedorId: String): Flow<List<ItemHistorialRegistroAcopio>>

    /**
     * Población on-demand de `registro_acopio_cache` para armar el picker "elegí el registro padre"
     * cuando es ajeno (`§4.2` caso 3, `§5` fila `ObtenerRegistrosDeProveedorUseCase`). Clasificación
     * ONLINE+CACHE (`§5` de la arquitectura): intenta refrescar desde red y cachear; si falla, degrada a
     * lo que ya esté cacheado -- nunca propaga un error de dominio acá, a diferencia de la resolución de
     * padre en `crear()` de los hijos (`§4.2.a`), que sí debe fallar explícitamente.
     */
    suspend fun obtenerRegistrosDeProveedor(proveedorId: String): List<RegistroAcopioReferencia>

    /** Fuerza un reintento fuera del ciclo automático (`ReintentarManualUseCase`, `§5`). */
    fun reintentar(uuidCliente: String)

    /** Logout con 0 pendientes (`§6`): borra el historial ya confirmado de la sesión activa. */
    suspend fun purgarSincronizados()

    /** Logout (`§6`, C-09 RNF-12): `registro_acopio_cache` es una de las 3 tablas con datos personales. */
    fun borrarCacheAjenos()
}

class RegistroAcopioRepositoryImpl(
    private val gestorSesion: GestorSesion,
    private val local: RegistroAcopioLocalDataSource,
    private val cacheLocal: RegistroAcopioCacheLocalDataSource,
    private val apiClient: ApiClient,
    private val syncEngine: SyncEngine,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : RegistroAcopioRepository {

    override suspend fun crear(datos: NuevoRegistroAcopio): String {
        val usuarioId = gestorSesion.sesionActual()?.usuarioId
            ?: error("crear() llamado sin sesión activa -- la UI no debería permitir capturar sin login")
        val uuidCliente = generarUuidV4()

        local.insertar(
            RegistroAcopio(
                uuidCliente = uuidCliente,
                serverId = null,
                usuarioId = usuarioId,
                proveedorId = datos.proveedorId,
                unidadId = datos.unidadId,
                fechaHora = datos.fechaHora,
                litros = datos.litros,
                gpsLat = datos.gpsLat,
                gpsLng = datos.gpsLng,
                motivoObservacionId = datos.motivoObservacionId,
                litrosPorVoz = datos.litrosPorVoz,
                syncStatus = SyncStatus.PENDING,
                syncAttempts = 0,
                syncError = null,
                nextAttemptAt = null,
                creadoEn = ahoraComoFechaHora(reloj, zona),
                sincronizadoEn = null,
            ),
        )
        // Decisión de diseño (§4.1, documentada en el checkpoint): el Repository dispara el sync
        // oportunista, no el UseCase -- el UseCase no debería saber que existe un SyncEngine (§2/§16.1).
        syncEngine.solicitarSyncOportunista()
        return uuidCliente
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarPendientes(): Flow<List<RegistroAcopio>> =
        gestorSesion.sesion.flatMapLatest { sesion ->
            val usuarioId = sesion?.usuarioId ?: return@flatMapLatest flowOf(emptyList())
            local.observarTodos(usuarioId)
        }

    override fun observarHistorialProveedor(proveedorId: String): Flow<List<ItemHistorialRegistroAcopio>> =
        combine(local.observarPorProveedor(proveedorId), cacheLocal.observarPorProveedor(proveedorId)) { propios, ajenos ->
            val idsPropiosConSeverId = propios.mapNotNull { it.serverId }.toSet()
            propios.map { ItemHistorialRegistroAcopio.Propio(it) } +
                ajenos.filterNot { it.id in idsPropiosConSeverId }.map { ItemHistorialRegistroAcopio.Ajeno(it) }
        }

    override suspend fun obtenerRegistrosDeProveedor(proveedorId: String): List<RegistroAcopioReferencia> {
        when (val respuesta = apiClient.get<List<RegistroAcopioResumenResponse>>(Endpoints.registrosAcopioPorProveedor(proveedorId))) {
            is ApiResult.Exito -> {
                val ahora = ahoraComoFechaHora(reloj, zona)
                respuesta.datos.forEach { resumen ->
                    cacheLocal.upsert(resumen.aReferenciaCacheDeResumen(proveedorId, ahora))
                }
            }
            // ONLINE+CACHE (§5): sin red, se degrada a lo que ya esté cacheado -- no se propaga error acá.
            is ApiResult.Error -> Unit
        }
        return cacheLocal.obtenerPorProveedor(proveedorId)
    }

    override fun reintentar(uuidCliente: String) {
        local.actualizarEstadoSync(uuidCliente, SyncStatus.PENDING, syncAttempts = 0, syncError = null, nextAttemptAt = null)
        syncEngine.solicitarSyncOportunista()
    }

    override suspend fun purgarSincronizados() {
        val usuarioId = gestorSesion.sesionActual()?.usuarioId ?: return
        local.eliminarSincronizadosDeUsuario(usuarioId)
    }

    override fun borrarCacheAjenos() {
        cacheLocal.borrarTodo()
    }

    private fun RegistroAcopioResumenResponse.aReferenciaCacheDeResumen(
        proveedorId: String,
        actualizadoEn: LocalDateTime,
    ): RegistroAcopioReferencia = RegistroAcopioReferencia(
        id = id,
        uuidCliente = null, // DATA-013: el DTO resumen no lo trae
        proveedorId = proveedorId, // no viene en el DTO, pero es el path param que ya conocemos
        proveedorNombre = null,
        fechaHora = fechaHora,
        litros = litros,
        tieneObservacion = tieneObservacion,
        origen = Origen.RESUMEN,
        actualizadoEn = actualizadoEn,
    )
}
