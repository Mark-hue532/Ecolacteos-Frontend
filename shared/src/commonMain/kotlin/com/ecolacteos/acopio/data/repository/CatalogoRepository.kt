package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.core.ApiError
import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.data.local.datasource.CatalogosLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RutaZonaLocalDataSource
import com.ecolacteos.acopio.data.remote.dto.ProveedorPublicoResponse
import com.ecolacteos.acopio.data.remote.dto.RutaProveedorOrdenResponse
import com.ecolacteos.acopio.domain.model.Comunicado
import com.ecolacteos.acopio.domain.model.MotivoObservacion
import com.ecolacteos.acopio.domain.model.PrecioLitroVigente
import com.ecolacteos.acopio.domain.model.PrediccionProveedor
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.model.RutaProveedorOrden
import com.ecolacteos.acopio.domain.model.TipoQueso
import com.ecolacteos.acopio.domain.model.Unidad
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.synchronization.SyncEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Resultado de resolver un QR (`A-02`, `MOBILE_SCREENS.md §5`) -- distingue los tres caminos del diagrama
 * de flujo del documento, cada uno con una UI distinta. Nunca colapsa "no encontrado" y "sin señal para
 * consultar" en el mismo caso: son mensajes y acciones diferentes.
 */
sealed interface ResultadoResolucionQr {
    data class Encontrado(val proveedor: Proveedor) : ResultadoResolucionQr

    /** 404 real del backend -- el código no corresponde a ningún proveedor. */
    data object NoEncontrado : ResultadoResolucionQr

    /** No está en `proveedor_cache` y no hay conexión para consultar `GET /api/proveedores/qr/{codigoQr}`. */
    data object SinSenalParaConsultar : ResultadoResolucionQr
}

/**
 * Catálogos (`PROMPT_FASE_06.md §4.3`): proveedores, unidades, motivos, tipos de queso, precio vigente,
 * comunicados, predicciones -- todo solo lectura reactiva sobre lo que el Sync Engine (Fase 5) ya deja
 * poblado en `/sync/cambios` al final de cada ciclo. Esta fase **no vuelve a implementar ese fetch**.
 *
 * `ruta_zona_cache` es la excepción (`§18.5`): se descarga bajo demanda, no viaja en `/sync/cambios`, así
 * que sí le corresponde un método propio que llama a la red directo.
 */
interface CatalogoRepository {
    fun observarProveedores(): Flow<List<Proveedor>>
    fun observarUnidades(): Flow<List<Unidad>>
    fun observarMotivosObservacion(): Flow<List<MotivoObservacion>>
    fun observarTiposQueso(): Flow<List<TipoQueso>>
    fun observarComunicados(): Flow<List<Comunicado>>
    fun observarPredicciones(): Flow<List<PrediccionProveedor>>

    /** `null` colapsa "nunca se sincronizó" y "el backend no tiene precio vigente" -- ver Fase 4. */
    fun obtenerPrecioVigente(): PrecioLitroVigente?

    /** Refresco manual (futuro "pull to refresh") -- delega en el Sync Engine, no reimplementa el `GET`. */
    fun refrescar()

    /**
     * `GET /zonas/{zonaId}/ruta` (`§18.5`), ONLINE+CACHE: refresca y reemplaza (acotado a esa zona, nunca
     * borrado de la tabla entera) si hay red; si falla, degrada a lo último cacheado para esa zona.
     */
    suspend fun obtenerRutaDelDia(zonaId: String): List<RutaProveedorOrden>

    /**
     * Escaneo de QR (`A-02`, `MOBILE_SCREENS.md §5`): **resuelve contra SQLite primero, sin llamado de
     * red** -- clasificación OFFLINE REAL, tiene que funcionar con cero conectividad. Solo si el código no
     * está en `proveedor_cache` intenta `GET /api/proveedores/qr/{codigoQr}`; un 200 se guarda en cache
     * (fila nueva, sin tocar el resto de la tabla) antes de devolverlo. Ver [ResultadoResolucionQr].
     */
    suspend fun resolverProveedorPorQr(codigoQr: String): ResultadoResolucionQr

    /**
     * Logout (`§6`, C-09 RNF-12): `proveedor_cache` y `ruta_zona_cache` son 2 de las 3 tablas con datos
     * personales de proveedores que se borran siempre al cerrar sesión -- la 3ra (`registro_acopio_cache`)
     * vive en `RegistroAcopioRepository.borrarCacheAjenos`, que ya tiene esa dependencia inyectada.
     */
    fun borrarDatosPersonales()
}

class CatalogoRepositoryImpl(
    private val catalogosLocal: CatalogosLocalDataSource,
    private val rutaZonaLocal: RutaZonaLocalDataSource,
    private val apiClient: ApiClient,
    private val syncEngine: SyncEngine,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : CatalogoRepository {

    override fun observarProveedores(): Flow<List<Proveedor>> = catalogosLocal.observarProveedores()
    override fun observarUnidades(): Flow<List<Unidad>> = catalogosLocal.observarUnidades()
    override fun observarMotivosObservacion(): Flow<List<MotivoObservacion>> = catalogosLocal.observarMotivosObservacion()
    override fun observarTiposQueso(): Flow<List<TipoQueso>> = catalogosLocal.observarTiposQueso()
    override fun observarComunicados(): Flow<List<Comunicado>> = catalogosLocal.observarComunicados()
    override fun observarPredicciones(): Flow<List<PrediccionProveedor>> = catalogosLocal.observarPredicciones()
    override fun obtenerPrecioVigente(): PrecioLitroVigente? = catalogosLocal.obtenerPrecioVigente()

    override fun refrescar() {
        syncEngine.solicitarSyncOportunista()
    }

    override suspend fun obtenerRutaDelDia(zonaId: String): List<RutaProveedorOrden> {
        when (val respuesta = apiClient.get<List<RutaProveedorOrdenResponse>>(Endpoints.rutaDeZona(zonaId))) {
            is ApiResult.Exito -> {
                val ahora = ahoraComoFechaHora(reloj, zona)
                rutaZonaLocal.reemplazarPorZona(zonaId, respuesta.datos.map { it.aDominio(zonaId, ahora) })
            }
            // ONLINE+CACHE: sin red, se degrada a la última ruta cacheada para esta zona.
            is ApiResult.Error -> Unit
        }
        return rutaZonaLocal.obtenerPorZona(zonaId)
    }

    override suspend fun resolverProveedorPorQr(codigoQr: String): ResultadoResolucionQr {
        val enCache = observarProveedores().first().firstOrNull { it.codigoQr == codigoQr }
        if (enCache != null) return ResultadoResolucionQr.Encontrado(enCache)

        return when (val respuesta = apiClient.get<ProveedorPublicoResponse>(Endpoints.proveedorPorQr(codigoQr))) {
            is ApiResult.Exito -> {
                val proveedor = respuesta.datos.aDominio(ahoraComoFechaHora(reloj, zona))
                catalogosLocal.upsertProveedor(proveedor)
                ResultadoResolucionQr.Encontrado(proveedor)
            }
            is ApiResult.Error -> when (respuesta.error) {
                is ApiError.NoEncontrado -> ResultadoResolucionQr.NoEncontrado
                // Cualquier otro fallo (sin conexión, timeout, 5xx) se trata igual que "sin señal para
                // consultar" -- el documento (§5) solo distingue 200/404 de "no hay conexión"; un 5xx real
                // en pleno escaneo es un caso de borde que el contrato no cubre, y degradarlo al mismo
                // mensaje que "sin señal" es más seguro que inventar un tercer mensaje sin especificar.
                else -> ResultadoResolucionQr.SinSenalParaConsultar
            }
        }
    }

    override fun borrarDatosPersonales() {
        catalogosLocal.borrarProveedores()
        rutaZonaLocal.borrarTodo()
    }

    private fun RutaProveedorOrdenResponse.aDominio(zonaId: String, actualizadoEn: LocalDateTime): RutaProveedorOrden =
        RutaProveedorOrden(
            zonaId = zonaId,
            proveedorId = proveedorId,
            proveedorNombre = proveedorNombre,
            orden = orden,
            horaEstimada = horaEstimada,
            actualizadoEn = actualizadoEn,
        )

    /** Mismo DTO que `CambiosResponse.proveedores` (`MOBILE_DATA_MAPPING.md §5.6`) -- ver `ProveedorPublicoResponse`. */
    private fun ProveedorPublicoResponse.aDominio(actualizadoEn: LocalDateTime): Proveedor = Proveedor(
        id = id,
        nombre = nombre,
        zonaActualId = zonaActualId,
        zonaActualNombre = zonaActualNombre,
        codigoQr = codigoQr,
        actualizadoEn = actualizadoEn,
    )
}
