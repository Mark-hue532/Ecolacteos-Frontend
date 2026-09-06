package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.core.generarUuidV4
import com.ecolacteos.acopio.data.local.datasource.VentaLocalDataSource
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.data.remote.dto.VentaResponse
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.Venta
import com.ecolacteos.acopio.domain.model.VentaDetalle
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.synchronization.SyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** Datos que la UI ya validó para crear una `Venta` (`§4.1`). */
data class NuevaVenta(
    val fecha: LocalDate,
    val tipoCliente: TipoClienteVenta,
    val tipoQuesoId: String,
    val cantidad: Int,
    val precioUnitario: Decimal,
)

/** `Venta` (`PROMPT_FASE_06.md §4.1`) -- mismo patrón que `RegistroAcopioRepository`, sin dependencias. */
interface VentaRepository {
    suspend fun crear(datos: NuevaVenta): String
    fun observarPendientes(): Flow<List<Venta>>
    fun reintentar(uuidCliente: String)
    suspend fun purgarSincronizados()

    /**
     * `V-03` (`MOBILE_SCREENS.md §8`, `PROMPT_FASE_07.md §2.4`): local primero, y si ya hay `server_id` y
     * hay señal, refresca contra `GET /api/ventas/{id}` para traer `total`/`tipoQuesoNombre` reales -- el
     * único lugar del contrato que los tiene (`venta_local` no los persiste, ver checkpoint de la Fase 7).
     * Degrada a los datos locales (con `total`/`tipoQuesoNombre` en `null`) si la venta no sincronizó
     * todavía o si la llamada falla -- nunca lanza, nunca inventa un total. `null` solo si [uuidCliente] no
     * existe en absoluto.
     */
    suspend fun obtenerDetalle(uuidCliente: String): VentaDetalle?
}

class VentaRepositoryImpl(
    private val gestorSesion: GestorSesion,
    private val local: VentaLocalDataSource,
    private val syncEngine: SyncEngine,
    private val apiClient: ApiClient,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : VentaRepository {

    override suspend fun crear(datos: NuevaVenta): String {
        val usuarioId = gestorSesion.sesionActual()?.usuarioId
            ?: error("crear() llamado sin sesión activa -- la UI no debería permitir capturar sin login")
        val uuidCliente = generarUuidV4()

        local.insertar(
            Venta(
                uuidCliente = uuidCliente,
                serverId = null,
                usuarioId = usuarioId,
                fecha = datos.fecha,
                tipoCliente = datos.tipoCliente,
                tipoQuesoId = datos.tipoQuesoId,
                cantidad = datos.cantidad,
                precioUnitario = datos.precioUnitario,
                syncStatus = SyncStatus.PENDING,
                syncAttempts = 0,
                syncError = null,
                nextAttemptAt = null,
                creadoEn = ahoraComoFechaHora(reloj, zona),
                sincronizadoEn = null,
            ),
        )
        syncEngine.solicitarSyncOportunista()
        return uuidCliente
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarPendientes(): Flow<List<Venta>> =
        gestorSesion.sesion.flatMapLatest { sesion ->
            val usuarioId = sesion?.usuarioId ?: return@flatMapLatest flowOf(emptyList())
            local.observarTodos(usuarioId)
        }

    override fun reintentar(uuidCliente: String) {
        local.actualizarEstadoSync(uuidCliente, SyncStatus.PENDING, syncAttempts = 0, syncError = null, nextAttemptAt = null)
        syncEngine.solicitarSyncOportunista()
    }

    override suspend fun obtenerDetalle(uuidCliente: String): VentaDetalle? {
        val venta = local.obtenerPorUuidCliente(uuidCliente) ?: return null
        val serverId = venta.serverId
        if (serverId != null) {
            val respuesta = apiClient.get<VentaResponse>(Endpoints.ventaPorId(serverId))
            if (respuesta is ApiResult.Exito) return venta.aDetalle(serverId, respuesta.datos)
        }
        // Sin server_id todavia (no sincronizo) o la llamada fallo (sin conexion, error) -- ONLINE+CACHE
        // degrada a lo que ya sabemos local, sin inventar total ni tipoQuesoNombre (`§8`, `DATA-002`).
        return venta.aDetalle(serverId, respuestaRemota = null)
    }

    override suspend fun purgarSincronizados() {
        val usuarioId = gestorSesion.sesionActual()?.usuarioId ?: return
        local.eliminarSincronizadosDeUsuario(usuarioId)
    }
}

private fun Venta.aDetalle(serverId: String?, respuestaRemota: VentaResponse?): VentaDetalle = VentaDetalle(
    uuidCliente = uuidCliente,
    serverId = serverId,
    fecha = fecha,
    tipoCliente = tipoCliente,
    tipoQuesoId = tipoQuesoId,
    tipoQuesoNombre = respuestaRemota?.tipoQuesoNombre,
    cantidad = cantidad,
    precioUnitario = precioUnitario,
    total = respuestaRemota?.total,
    syncStatus = syncStatus,
)
