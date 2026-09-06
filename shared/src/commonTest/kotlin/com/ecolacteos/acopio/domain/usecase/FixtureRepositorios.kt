package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.local.DispatcherProviderDeTest
import com.ecolacteos.acopio.data.local.crearAcopioDatabase
import com.ecolacteos.acopio.data.local.crearDriverDeTest
import com.ecolacteos.acopio.data.local.datasource.AnalisisCalidadLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.CatalogosLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.LoteProduccionLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RegistroAcopioCacheLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RegistroAcopioLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RutaZonaLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.VentaLocalDataSource
import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepositoryImpl
import com.ecolacteos.acopio.data.repository.CatalogoRepository
import com.ecolacteos.acopio.data.repository.CatalogoRepositoryImpl
import com.ecolacteos.acopio.data.repository.ComunicadoConfirmacionRepository
import com.ecolacteos.acopio.data.repository.ComunicadoConfirmacionRepositoryImpl
import com.ecolacteos.acopio.data.repository.CorreccionRegistroRepository
import com.ecolacteos.acopio.data.repository.CorreccionRegistroRepositoryImpl
import com.ecolacteos.acopio.data.repository.LoteProduccionRepository
import com.ecolacteos.acopio.data.repository.LoteProduccionRepositoryImpl
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepositoryImpl
import com.ecolacteos.acopio.data.repository.ResolutorPadreRegistroAcopio
import com.ecolacteos.acopio.data.repository.VentaRepository
import com.ecolacteos.acopio.data.repository.VentaRepositoryImpl
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.ApiConfig
import com.ecolacteos.acopio.network.Entorno
import com.ecolacteos.acopio.network.TokenProviderEnMemoria
import com.ecolacteos.acopio.network.configurarPluginsComunes
import com.ecolacteos.acopio.synchronization.ConnectivityObserverFake
import com.ecolacteos.acopio.synchronization.GestorSesionFake
import com.ecolacteos.acopio.synchronization.RelojFijo
import com.ecolacteos.acopio.synchronization.SyncEngine
import com.ecolacteos.acopio.synchronization.SyncEngineImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Toda la app cableada en forma de test (`PROMPT_FASE_06.md §9`): los 7 `LocalDataSource` de Fase 4, el
 * `SyncEngine` real de Fase 5, y los 7 `Repository` + `ResolutorPadreRegistroAcopio` de esta fase, sobre
 * SQLite en memoria + `MockEngine`. Reusa los fakes de `synchronization/FixtureDeSync.kt` (Fase 5) --
 * `GestorSesionFake`, `ConnectivityObserverFake`, `RelojFijo` -- en vez de duplicarlos.
 *
 * No expone los 15 `UseCase` como propiedades separadas: son wrappers delgados de una línea (`§5`), los
 * tests los instancian directo donde hacen falta para dejar claro cuál es el que se está probando.
 */
class FixtureRepositorios(
    val reloj: RelojFijo = RelojFijo(Instant.parse("2026-09-06T12:00:00Z")),
    val gestorSesion: GestorSesionFake = GestorSesionFake(),
    val conectividad: ConnectivityObserverFake = ConnectivityObserverFake(),
    manejador: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    private val zona = TimeZone.UTC
    private val database = crearAcopioDatabase(crearDriverDeTest())

    val registrosLocal = RegistroAcopioLocalDataSource(database.registroAcopioLocalQueries, DispatcherProviderDeTest)
    val analisisLocal = AnalisisCalidadLocalDataSource(database.analisisCalidadLocalQueries, DispatcherProviderDeTest)
    val lotesLocal = LoteProduccionLocalDataSource(
        database.loteProduccionLocalQueries,
        database.loteProduccionRegistroLocalQueries,
        DispatcherProviderDeTest,
    )
    val ventasLocal = VentaLocalDataSource(database.ventaLocalQueries, DispatcherProviderDeTest)
    val cacheLocal = RegistroAcopioCacheLocalDataSource(database.registroAcopioCacheQueries, DispatcherProviderDeTest)
    val rutaZonaLocal = RutaZonaLocalDataSource(database.rutaZonaCacheQueries)
    val catalogosLocal = CatalogosLocalDataSource(
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

    val rutasPedidas = mutableListOf<String>()
    private val apiConfig = ApiConfig(entorno = Entorno.DEV, baseUrl = "https://api.test")
    val apiClient = ApiClient(
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

    val syncEngine: SyncEngine = SyncEngineImpl(
        apiClient = apiClient,
        gestorSesion = gestorSesion,
        registrosLocal = registrosLocal,
        analisisLocal = analisisLocal,
        lotesLocal = lotesLocal,
        ventasLocal = ventasLocal,
        catalogosLocal = catalogosLocal,
        conectividad = conectividad,
        dispatchers = DispatcherProviderDeTest,
        reloj = reloj,
        zona = zona,
    )

    /**
     * Lo que reciben los `Repository`: el mismo motor, pero con `solicitarSyncOportunista()` neutralizado.
     * En producción ese disparo fire-and-forget (§4.1, cada `crear()`) convive bien con un `ejecutarCiclo()`
     * concurrente -- el que pierde el `Mutex` de "un ciclo a la vez" (§6.7) simplemente no hacía falta. En un
     * test que necesita el punto exacto en el que corre un ciclo (para armar un escenario y recién ahí
     * evaluarlo), esa carrera es pura fuente de `YaEnCurso` espurio: se vio en la práctica en
     * `RepositorioCreacionTest`. `fixture.syncEngine` (arriba, sin envolver) es el que los tests llaman a
     * mano para controlar ese punto.
     */
    private val syncEngineParaRepositorios: SyncEngine = SyncEngineSinOportunista(syncEngine)

    private val resolutor = ResolutorPadreRegistroAcopio(registrosLocal, cacheLocal, apiClient, reloj, zona)

    val registroAcopioRepository: RegistroAcopioRepository =
        RegistroAcopioRepositoryImpl(gestorSesion, registrosLocal, cacheLocal, apiClient, syncEngineParaRepositorios, reloj, zona)
    val ventaRepository: VentaRepository =
        VentaRepositoryImpl(gestorSesion, ventasLocal, syncEngineParaRepositorios, reloj, zona)
    val analisisCalidadRepository: AnalisisCalidadRepository =
        AnalisisCalidadRepositoryImpl(gestorSesion, analisisLocal, resolutor, syncEngineParaRepositorios, reloj, zona)
    val loteProduccionRepository: LoteProduccionRepository =
        LoteProduccionRepositoryImpl(gestorSesion, lotesLocal, resolutor, syncEngineParaRepositorios, reloj, zona)
    val catalogoRepository: CatalogoRepository =
        CatalogoRepositoryImpl(catalogosLocal, rutaZonaLocal, apiClient, syncEngineParaRepositorios, reloj, zona)
    val correccionRegistroRepository: CorreccionRegistroRepository = CorreccionRegistroRepositoryImpl(apiClient)
    val comunicadoConfirmacionRepository: ComunicadoConfirmacionRepository = ComunicadoConfirmacionRepositoryImpl(apiClient)

    val verificarPendientes = VerificarPendientesUseCase(
        registroAcopioRepository,
        analisisCalidadRepository,
        loteProduccionRepository,
        ventaRepository,
    )
    val logout = LogoutUseCase(
        gestorSesion,
        verificarPendientes,
        registroAcopioRepository,
        analisisCalidadRepository,
        loteProduccionRepository,
        ventaRepository,
        catalogoRepository,
    )

    fun cuantasVecesSePidio(ruta: String): Int = rutasPedidas.count { it == ruta }
}

/** Ver el comentario de [FixtureRepositorios.syncEngineParaRepositorios] -- por qué existe este envoltorio. */
private class SyncEngineSinOportunista(private val real: SyncEngine) : SyncEngine by real {
    override fun solicitarSyncOportunista() {
        // No-op a propósito.
    }
}
