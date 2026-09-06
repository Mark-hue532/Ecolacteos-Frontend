package com.ecolacteos.acopio.synchronization

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.local.DispatcherProviderDeTest
import com.ecolacteos.acopio.data.local.crearAcopioDatabase
import com.ecolacteos.acopio.data.local.crearDriverDeTest
import com.ecolacteos.acopio.data.local.datasource.AnalisisCalidadLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.CatalogosLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.LoteProduccionLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RegistroAcopioLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.VentaLocalDataSource
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.domain.model.AnalisisCalidad
import com.ecolacteos.acopio.domain.model.LoteProduccion
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.Venta
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.ApiConfig
import com.ecolacteos.acopio.network.Entorno
import com.ecolacteos.acopio.network.TokenProviderEnMemoria
import com.ecolacteos.acopio.network.configurarPluginsComunes
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Arma un [SyncEngineImpl] real contra: SQLite en memoria (driver de Fase 4), `MockEngine` de Ktor sobre la
 * MISMA configuración de plugins que producción (`configurarPluginsComunes`, igual criterio que
 * `ApiClientTest` de Fase 2), un [ConnectivityObserverFake] y un [RelojFijo].
 *
 * Nada acá mockea al motor ni a los `LocalDataSource`: lo único falso es la red, el reloj y la señal de
 * conectividad -- las transiciones de estado se verifican leyendo SQLite de verdad.
 */
class FixtureDeSync(
    val reloj: RelojFijo = RelojFijo(AHORA_FIJO),
    val conectividad: ConnectivityObserverFake = ConnectivityObserverFake(),
    val gestorSesion: GestorSesionFake = GestorSesionFake(),
    manejador: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    private val database = crearAcopioDatabase(crearDriverDeTest())

    val registros = RegistroAcopioLocalDataSource(database.registroAcopioLocalQueries, DispatcherProviderDeTest)
    val analisis = AnalisisCalidadLocalDataSource(database.analisisCalidadLocalQueries, DispatcherProviderDeTest)
    val lotes = LoteProduccionLocalDataSource(
        database.loteProduccionLocalQueries,
        database.loteProduccionRegistroLocalQueries,
        DispatcherProviderDeTest,
    )
    val ventas = VentaLocalDataSource(database.ventaLocalQueries, DispatcherProviderDeTest)
    val catalogos = CatalogosLocalDataSource(
        database.proveedorCacheQueries,
        database.unidadCacheQueries,
        database.motivoObservacionCacheQueries,
        database.tipoQuesoCacheQueries,
        database.comunicadoCacheQueries,
        database.comunicadoZonaCacheQueries,
        database.prediccionProveedorCacheQueries,
        database.precioLitroVigenteCacheQueries,
        DispatcherProviderDeTest,
    )

    /** Rutas pedidas, en orden -- el test de orden entre recursos (trampa #1) se apoya en esto. */
    val rutasPedidas = mutableListOf<String>()

    private val apiConfig = ApiConfig(entorno = Entorno.DEV, baseUrl = "https://api.test")

    private val apiClient = ApiClient(
        HttpClient(
            MockEngine { request ->
                rutasPedidas += request.url.encodedPath
                manejador(request)
            },
        ) {
            configurarPluginsComunes(apiConfig, TokenProviderEnMemoria("token-de-prueba"), debug = false)
        },
        apiConfig,
    )

    val engine: SyncEngine = SyncEngineImpl(
        apiClient = apiClient,
        gestorSesion = gestorSesion,
        registrosLocal = registros,
        analisisLocal = analisis,
        lotesLocal = lotes,
        ventasLocal = ventas,
        catalogosLocal = catalogos,
        conectividad = conectividad,
        dispatchers = DispatcherProviderDeTest,
        reloj = reloj,
        zona = TimeZone.UTC,
    )

    fun cuantasVecesSePidio(ruta: String): Int = rutasPedidas.count { it == ruta }

    // -- Semillas -------------------------------------------------------------------------------

    fun sembrarRegistro(
        uuidCliente: String,
        estado: SyncStatus = SyncStatus.PENDING,
        serverId: String? = null,
        intentos: Int = 0,
    ) {
        registros.insertar(
            RegistroAcopio(
                uuidCliente = uuidCliente,
                serverId = serverId,
                usuarioId = GestorSesionFake.USUARIO_ID,
                proveedorId = "prov-1",
                unidadId = "unidad-1",
                fechaHora = LocalDateTime(2026, 9, 5, 6, 0, 0),
                litros = Decimal.parseString("120.50"),
                gpsLat = Decimal.parseString("-12.045678"),
                gpsLng = Decimal.parseString("-77.030348"),
                motivoObservacionId = null,
                litrosPorVoz = false,
                syncStatus = estado,
                syncAttempts = intentos,
                syncError = null,
                nextAttemptAt = null,
                creadoEn = LocalDateTime(2026, 9, 5, 6, 0, 1),
                sincronizadoEn = null,
            ),
        )
    }

    fun sembrarAnalisis(
        uuidCliente: String,
        padreUuidCliente: String? = null,
        padreServerId: String? = null,
        estado: SyncStatus = SyncStatus.PENDING,
    ) {
        analisis.insertar(
            AnalisisCalidad(
                uuidCliente = uuidCliente,
                serverId = null,
                usuarioId = GestorSesionFake.USUARIO_ID,
                registroAcopioUuidCliente = padreUuidCliente,
                registroAcopioServerId = padreServerId,
                folioMuestra = "F-$uuidCliente",
                agua = Decimal.parseString("3.20"),
                proteina = null,
                lactosa = null,
                densidad = null,
                temperatura = null,
                ph = null,
                aguaAnadida = false,
                syncStatus = estado,
                syncAttempts = 0,
                syncError = null,
                nextAttemptAt = null,
                creadoEn = LocalDateTime(2026, 9, 5, 7, 0, 0),
                sincronizadoEn = null,
            ),
        )
    }

    fun sembrarLote(uuidCliente: String, estado: SyncStatus = SyncStatus.PENDING) {
        lotes.insertar(
            LoteProduccion(
                uuidCliente = uuidCliente,
                serverId = null,
                usuarioId = GestorSesionFake.USUARIO_ID,
                fecha = LocalDate(2026, 9, 5),
                tipoQuesoId = "queso-1",
                litrosUsados = Decimal.parseString("500.00"),
                unidadesObtenidas = 40,
                syncStatus = estado,
                syncAttempts = 0,
                syncError = null,
                nextAttemptAt = null,
                creadoEn = LocalDateTime(2026, 9, 5, 8, 0, 0),
                sincronizadoEn = null,
            ),
        )
    }

    fun sembrarVenta(uuidCliente: String, estado: SyncStatus = SyncStatus.PENDING) {
        ventas.insertar(
            Venta(
                uuidCliente = uuidCliente,
                serverId = null,
                usuarioId = GestorSesionFake.USUARIO_ID,
                fecha = LocalDate(2026, 9, 5),
                tipoCliente = TipoClienteVenta.MAYORISTA,
                tipoQuesoId = "queso-1",
                cantidad = 10,
                precioUnitario = Decimal.parseString("25.50"),
                syncStatus = estado,
                syncAttempts = 0,
                syncError = null,
                nextAttemptAt = null,
                creadoEn = LocalDateTime(2026, 9, 5, 9, 0, 0),
                sincronizadoEn = null,
            ),
        )
    }

    companion object {
        val AHORA_FIJO: Instant = Instant.parse("2026-09-05T12:00:00Z")
    }
}

private val CABECERAS_JSON = headersOf(HttpHeaders.ContentType, "application/json")

/** Cuerpo de `SyncResultResponse` (`MOBILE_DATA_MAPPING.md §5.6`) -- solo uuidClientes, sin ids de servidor. */
fun cuerpoSync(confirmados: List<String> = emptyList(), errores: Map<String, String> = emptyMap()): String {
    val listaConfirmados = confirmados.joinToString(",") { "\"$it\"" }
    val listaErrores = errores.entries.joinToString(",") { (uuid, motivo) ->
        """{"uuidCliente":"$uuid","motivo":"$motivo"}"""
    }
    return """{"confirmados":[$listaConfirmados],"errores":[$listaErrores]}"""
}

/** `CambiosResponse` mínimo y válido: todas las listas vacías, sin precio vigente configurado. */
fun cuerpoCambiosVacio(): String = """
    {"generadoEn":"2026-09-05T12:00:00Z","proveedores":[],"comunicados":[],"prediccionesProveedor":[],
     "motivosObservacion":[],"tiposQueso":[],"unidades":[]}
""".trimIndent()

fun MockRequestHandleScope.responderJson(cuerpo: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = cuerpo, status = status, headers = CABECERAS_JSON)
