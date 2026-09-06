package com.ecolacteos.acopio.synchronization

import com.ecolacteos.acopio.core.ApiError
import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.core.DispatcherProvider
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.data.local.datasource.AnalisisCalidadLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.CatalogosLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.LoteProduccionLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RegistroAcopioLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.VentaLocalDataSource
import com.ecolacteos.acopio.data.remote.dto.AnalisisCalidadRequest
import com.ecolacteos.acopio.data.remote.dto.CambiosResponse
import com.ecolacteos.acopio.data.remote.dto.CrearLoteRequest
import com.ecolacteos.acopio.data.remote.dto.RegistroAcopioDTO
import com.ecolacteos.acopio.data.remote.dto.SyncResultResponse
import com.ecolacteos.acopio.data.remote.dto.VentaRequest
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Implementación del motor de `MOBILE_ARCHITECTURE.md §6`.
 *
 * **`registro_acopio_cache` no se inyecta a propósito**: la resolución de un padre ajeno (§18.1, mecanismo
 * 2) no necesita leer esa tabla, porque el `server_id` ya viajó a la fila hija cuando la UI la creó. Leerla
 * acá agregaría un modo de falla inventado -- una fila de cache purgada por retención (§11.4) no invalida
 * un `server_id` que el hijo ya tiene.
 */
class SyncEngineImpl(
    private val apiClient: ApiClient,
    private val gestorSesion: GestorSesion,
    private val registrosLocal: RegistroAcopioLocalDataSource,
    private val analisisLocal: AnalisisCalidadLocalDataSource,
    private val lotesLocal: LoteProduccionLocalDataSource,
    private val ventasLocal: VentaLocalDataSource,
    private val catalogosLocal: CatalogosLocalDataSource,
    private val conectividad: ConnectivityObserver,
    private val dispatchers: DispatcherProvider,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : SyncEngine {

    private val estadoInterno = MutableStateFlow(EstadoSync.INACTIVO)
    override val estado: StateFlow<EstadoSync> = estadoInterno.asStateFlow()

    /** Un solo ciclo a la vez -- ver [ResultadoCiclo.YaEnCurso]. */
    private val enCurso = Mutex()

    /**
     * Alcance propio del motor, solo para los disparos fire-and-forget de [solicitarSyncOportunista] --
     * `observarConectividad` sigue recibiendo su `scope` por parámetro (API ya aprobada en Fase 5, no se
     * toca). Vive tanto como el singleton del motor (Koin), no hay un punto natural de cancelación externo
     * porque un intento de ciclo siempre termina solo (§6.7, motor interrumpible pero no "colgado").
     */
    private val alcancePropio = CoroutineScope(SupervisorJob() + dispatchers.io)

    override suspend fun ejecutarCiclo(): ResultadoCiclo {
        if (!enCurso.tryLock()) return ResultadoCiclo.YaEnCurso
        estadoInterno.value = EstadoSync.SINCRONIZANDO
        return try {
            withContext(dispatchers.io) { ciclo() }
        } finally {
            estadoInterno.value = EstadoSync.INACTIVO
            enCurso.unlock()
        }
    }

    override fun observarConectividad(scope: CoroutineScope): Job = scope.launch {
        conectividad.conectado
            .distinctUntilChanged()
            .filter { estaConectado -> estaConectado }
            .collect { ejecutarCiclo() }
    }

    override fun solicitarSyncOportunista() {
        alcancePropio.launch { ejecutarCiclo() }
    }

    private suspend fun ciclo(): ResultadoCiclo {
        val usuarioId = gestorSesion.sesionActual()?.usuarioId ?: return ResultadoCiclo.SinSesion
        val resumenes = mutableMapOf<RecursoSync, ResumenRecurso>()

        // Paso 1 -- RegistroAcopio ENTERO (envío + reconciliación) antes que sus hijos. El orden no es
        // estético: es lo único que permite que un padre confirmado en este ciclo promueva a sus hijos en
        // este mismo ciclo (§6.1, trampa #1).
        val registros = sincronizarRegistrosAcopio(usuarioId)
        registros.sesionInvalida?.let { return ResultadoCiclo.SesionInvalida(it) }
        resumenes[RecursoSync.REGISTRO_ACOPIO] = registros.resumen

        // Paso 2 -- hijos y Venta. Solo se envían las filas que ya eran enviables al empezar este paso;
        // las que no resuelven su padre caen a PENDING_DEPENDENCY con el motivo.
        val analisis = sincronizarAnalisisCalidad(usuarioId)
        analisis.sesionInvalida?.let { return ResultadoCiclo.SesionInvalida(it) }
        resumenes[RecursoSync.ANALISIS_CALIDAD] = analisis.resumen

        val lotes = sincronizarLotesProduccion(usuarioId)
        lotes.sesionInvalida?.let { return ResultadoCiclo.SesionInvalida(it) }
        resumenes[RecursoSync.LOTE_PRODUCCION] = lotes.resumen

        val ventas = sincronizarVentas(usuarioId)
        ventas.sesionInvalida?.let { return ResultadoCiclo.SesionInvalida(it) }
        resumenes[RecursoSync.VENTA] = ventas.resumen

        // Paso 3 -- promoción de PENDING_DEPENDENCY (§6.1). Las promovidas NO se reenvían en este ciclo:
        // quedan PENDING para el próximo, tal como pide el paso 6 de PROMPT_FASE_05.md §1.
        val promocion = promoverDependenciasResueltas(usuarioId)

        // Paso 4 -- catálogos. Independiente de todo lo anterior: corre aunque algún recurso haya fallado.
        val catalogos = refrescarCatalogos()
        catalogos.sesionInvalida?.let { return ResultadoCiclo.SesionInvalida(it) }

        return ResultadoCiclo.Completado(
            ResumenCiclo(
                porRecurso = resumenes,
                promovidosPorDependencia = promocion.promovidos,
                bloqueadosPorIdDePadre = promocion.bloqueados,
                catalogosActualizados = catalogos.actualizados,
                errorDeCatalogos = catalogos.error,
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Recursos
    // ---------------------------------------------------------------------------------------------

    private suspend fun sincronizarRegistrosAcopio(usuarioId: String): ResultadoRecurso {
        val pendientes = registrosLocal.obtenerPendientes(usuarioId, ahora())
        val escritor = escritorDe(
            marcar = { uuid, cuando -> registrosLocal.marcarSincronizado(uuid, serverId = null, sincronizadoEn = cuando) },
            actualizar = registrosLocal::actualizarEstadoSync,
        )
        val porUuid = pendientes.associate { it.uuidCliente to it.aRequest() }
        return enviarEnFragmentos(
            items = pendientes.map { ItemEnvio(it.uuidCliente, it.syncAttempts) },
            escritor = escritor,
            construirLote = { fragmento -> fragmento.mapNotNull { porUuid[it] } },
            enviar = { lote -> apiClient.postLista<RegistroAcopioDTO, SyncResultResponse>(Endpoints.SYNC_REGISTROS_ACOPIO, lote) },
        )
    }

    private suspend fun sincronizarVentas(usuarioId: String): ResultadoRecurso {
        val pendientes = ventasLocal.obtenerPendientes(usuarioId, ahora())
        val escritor = escritorDe(
            marcar = { uuid, cuando -> ventasLocal.marcarSincronizado(uuid, serverId = null, sincronizadoEn = cuando) },
            actualizar = ventasLocal::actualizarEstadoSync,
        )
        val porUuid = pendientes.associate { it.uuidCliente to it.aRequest() }
        return enviarEnFragmentos(
            items = pendientes.map { ItemEnvio(it.uuidCliente, it.syncAttempts) },
            escritor = escritor,
            construirLote = { fragmento -> fragmento.mapNotNull { porUuid[it] } },
            enviar = { lote -> apiClient.postLista<VentaRequest, SyncResultResponse>(Endpoints.SYNC_VENTAS, lote) },
        )
    }

    private suspend fun sincronizarAnalisisCalidad(usuarioId: String): ResultadoRecurso {
        val escritor = escritorDe(
            marcar = { uuid, cuando -> analisisLocal.marcarSincronizado(uuid, serverId = null, sincronizadoEn = cuando) },
            actualizar = analisisLocal::actualizarEstadoSync,
        )
        val pendientes = analisisLocal.obtenerPendientes(usuarioId, ahora())

        val enviables = mutableMapOf<String, AnalisisCalidadRequest>()
        var enEspera = 0
        pendientes.forEach { analisis ->
            when (val resolucion = resolverPadre(analisis.registroAcopioUuidCliente, analisis.registroAcopioServerId)) {
                is Resolucion.Resuelta -> enviables[analisis.uuidCliente] = analisis.aRequest(resolucion.registroAcopioId)
                else -> {
                    retenerPorDependencia(escritor, analisis.uuidCliente, analisis.syncAttempts, resolucion)
                    enEspera++
                }
            }
        }

        val resultado = enviarEnFragmentos(
            items = pendientes.filter { it.uuidCliente in enviables }.map { ItemEnvio(it.uuidCliente, it.syncAttempts) },
            escritor = escritor,
            construirLote = { fragmento -> fragmento.mapNotNull { enviables[it] } },
            enviar = { lote -> apiClient.postLista<AnalisisCalidadRequest, SyncResultResponse>(Endpoints.SYNC_ANALISIS_CALIDAD, lote) },
        )
        return resultado.copy(resumen = resultado.resumen.copy(enEsperaDeDependencia = enEspera))
    }

    private suspend fun sincronizarLotesProduccion(usuarioId: String): ResultadoRecurso {
        val escritor = escritorDe(
            marcar = { uuid, cuando -> lotesLocal.marcarSincronizado(uuid, serverId = null, sincronizadoEn = cuando) },
            actualizar = lotesLocal::actualizarEstadoSync,
        )
        val pendientes = lotesLocal.obtenerPendientes(usuarioId, ahora())

        val enviables = mutableMapOf<String, CrearLoteRequest>()
        var enEspera = 0
        pendientes.forEach { lote ->
            when (val resolucion = resolverRegistrosDelLote(lote.uuidCliente)) {
                is ResolucionLote.Resuelta -> enviables[lote.uuidCliente] = lote.aRequest(resolucion.registroAcopioIds)
                is ResolucionLote.Bloqueada -> {
                    retenerPorDependencia(escritor, lote.uuidCliente, lote.syncAttempts, resolucion.causa)
                    enEspera++
                }
            }
        }

        val resultado = enviarEnFragmentos(
            items = pendientes.filter { it.uuidCliente in enviables }.map { ItemEnvio(it.uuidCliente, it.syncAttempts) },
            escritor = escritor,
            construirLote = { fragmento -> fragmento.mapNotNull { enviables[it] } },
            enviar = { lote -> apiClient.postLista<CrearLoteRequest, SyncResultResponse>(Endpoints.SYNC_LOTES_PRODUCCION, lote) },
        )
        return resultado.copy(resumen = resultado.resumen.copy(enEsperaDeDependencia = enEspera))
    }

    // ---------------------------------------------------------------------------------------------
    // Envío genérico (§11.1: "una función sincronizar<T> parametrizada, no 4 implementaciones copiadas")
    // ---------------------------------------------------------------------------------------------

    private suspend fun <TReq> enviarEnFragmentos(
        items: List<ItemEnvio>,
        escritor: EscritorDeEstado,
        construirLote: (List<String>) -> List<TReq>,
        enviar: suspend (List<TReq>) -> ApiResult<SyncResultResponse>,
    ): ResultadoRecurso {
        var resumen = ResumenRecurso()
        items.chunked(PoliticaDeSync.TAMANO_FRAGMENTO).forEach { fragmento ->
            // SYNCING antes de enviar (paso 3 de §1 del prompt): si el proceso muere acá, la fila queda
            // marcada como "en vuelo" y el próximo ciclo la reintenta (§6.6).
            fragmento.forEach { escritor.actualizar(it.uuidCliente, SyncStatus.SYNCING, it.intentos, null, null) }

            val respuesta = enviar(construirLote(fragmento.map { it.uuidCliente }))
            resumen = resumen.copy(
                enviados = resumen.enviados + fragmento.size,
                fragmentosEnviados = resumen.fragmentosEnviados + 1,
            )

            when (respuesta) {
                is ApiResult.Exito -> resumen = reconciliar(fragmento, respuesta.datos, escritor, resumen)
                is ApiResult.Error -> {
                    if (respuesta.error is ApiError.NoAutorizado) {
                        return ResultadoRecurso(resumen, sesionInvalida = respuesta.error)
                    }
                    resumen = fallarFragmentoEntero(fragmento, respuesta.error, escritor, resumen)
                }
            }
        }
        return ResultadoRecurso(resumen)
    }

    /**
     * §6.2 al pie de la letra: `confirmados[]` → `SYNCED`; `errores[]` → `FAILED` permanente con el motivo
     * literal del backend; y **todo `uuidCliente` ausente de ambas listas se queda `SYNCING`** -- nunca se
     * asume éxito por omisión (trampa #2).
     */
    private fun reconciliar(
        fragmento: List<ItemEnvio>,
        respuesta: SyncResultResponse,
        escritor: EscritorDeEstado,
        resumenPrevio: ResumenRecurso,
    ): ResumenRecurso {
        val confirmados = respuesta.confirmados.toSet()
        val errores = respuesta.errores.associate { it.uuidCliente to it.motivo }
        var resumen = resumenPrevio
        val cuando = ahora()

        fragmento.forEach { item ->
            when {
                item.uuidCliente in confirmados -> {
                    escritor.marcar(item.uuidCliente, cuando)
                    resumen = resumen.copy(confirmados = resumen.confirmados + 1)
                }

                errores.containsKey(item.uuidCliente) -> {
                    // Permanente: sin next_attempt_at, no se reintenta solo (§6.1/§6.3).
                    escritor.actualizar(
                        item.uuidCliente,
                        SyncStatus.FAILED,
                        item.intentos + 1,
                        errores.getValue(item.uuidCliente),
                        null,
                    )
                    resumen = resumen.copy(fallidosPermanentes = resumen.fallidosPermanentes + 1)
                }

                else -> resumen = resumen.copy(sinRespuesta = resumen.sinRespuesta + 1)
            }
        }
        return resumen
    }

    /** El fragmento entero falló (timeout, 5xx, error de red, o un 4xx a nivel de lote). */
    private fun fallarFragmentoEntero(
        fragmento: List<ItemEnvio>,
        error: ApiError,
        escritor: EscritorDeEstado,
        resumenPrevio: ResumenRecurso,
    ): ResumenRecurso {
        var resumen = resumenPrevio
        fragmento.forEach { item ->
            val intentos = item.intentos + 1
            val proximo = if (error.esTransitorio) PoliticaDeSync.proximoIntento(intentos, reloj, zona) else null
            val esReintentable = error.esTransitorio && proximo != null
            val motivo = when {
                esReintentable -> error.mensaje
                error.esTransitorio -> "Se agotaron los ${PoliticaDeSync.MAXIMO_INTENTOS_AUTOMATICOS} " +
                    "reintentos automáticos. Último error: ${error.mensaje}"
                else -> error.mensaje
            }
            escritor.actualizar(item.uuidCliente, SyncStatus.FAILED, intentos, motivo, proximo)
            resumen = if (esReintentable) {
                resumen.copy(fallidosTransitorios = resumen.fallidosTransitorios + 1)
            } else {
                resumen.copy(fallidosPermanentes = resumen.fallidosPermanentes + 1)
            }
        }
        return resumen
    }

    // ---------------------------------------------------------------------------------------------
    // Dependencias (§18.1)
    // ---------------------------------------------------------------------------------------------

    /**
     * Pseudocódigo de §18.1, más la distinción que el contrato real obliga a hacer: un padre `SYNCED` sin
     * `server_id` no es "todavía no sincronizó", es [Resolucion.PadreSincronizadoSinId] -- el gap
     * `DATA-014`. Separarlos es lo que evita que el hijo espere en silencio para siempre.
     */
    private fun resolverPadre(uuidClienteDelPadre: String?, serverIdDelPadre: String?): Resolucion {
        if (serverIdDelPadre != null) return Resolucion.Resuelta(serverIdDelPadre)
        val uuidPadre = uuidClienteDelPadre ?: return Resolucion.PadreInexistente
        val padre = registrosLocal.obtenerPorUuidCliente(uuidPadre) ?: return Resolucion.PadreInexistente
        val serverId = padre.serverId
        return when {
            serverId != null -> Resolucion.Resuelta(serverId)
            padre.syncStatus == SyncStatus.SYNCED -> Resolucion.PadreSincronizadoSinId
            else -> Resolucion.PadreSinSincronizar
        }
    }

    /** Un lote entra completo o no entra: `registroAcopioIds` a medias cambiaría en silencio su significado. */
    private fun resolverRegistrosDelLote(loteUuidCliente: String): ResolucionLote {
        val registros = lotesLocal.obtenerRegistrosPorLote(loteUuidCliente)
        val ids = mutableListOf<String>()
        var bloqueo: Resolucion? = null
        registros.forEach { registro ->
            when (val resolucion = resolverPadre(registro.registroAcopioUuidCliente, registro.registroAcopioServerId)) {
                is Resolucion.Resuelta -> ids += resolucion.registroAcopioId
                // Se prioriza el bloqueo por DATA-014 sobre la espera normal: es el que hay que hacer visible.
                Resolucion.PadreSincronizadoSinId -> bloqueo = Resolucion.PadreSincronizadoSinId
                else -> if (bloqueo == null) bloqueo = resolucion
            }
        }
        val causa = bloqueo
        return if (causa == null) ResolucionLote.Resuelta(ids) else ResolucionLote.Bloqueada(causa)
    }

    private fun retenerPorDependencia(
        escritor: EscritorDeEstado,
        uuidCliente: String,
        intentos: Int,
        causa: Resolucion,
    ) {
        escritor.actualizar(uuidCliente, SyncStatus.PENDING_DEPENDENCY, intentos, causa.motivo(), null)
    }

    /**
     * Paso 6 de §1 del prompt: reevaluar las filas retenidas ahora que los padres de este ciclo ya se
     * reconciliaron. Las que resuelven pasan a `PENDING` **para el próximo ciclo** (no se reenvían acá).
     */
    private fun promoverDependenciasResueltas(usuarioId: String): Promocion {
        var promovidos = 0
        var bloqueados = 0

        analisisLocal.obtenerEnEsperaDeDependencia(usuarioId).forEach { analisis ->
            when (val resolucion = resolverPadre(analisis.registroAcopioUuidCliente, analisis.registroAcopioServerId)) {
                is Resolucion.Resuelta -> {
                    analisisLocal.actualizarEstadoSync(analisis.uuidCliente, SyncStatus.PENDING, analisis.syncAttempts, null, null)
                    promovidos++
                }

                Resolucion.PadreSincronizadoSinId -> {
                    analisisLocal.actualizarEstadoSync(
                        analisis.uuidCliente,
                        SyncStatus.PENDING_DEPENDENCY,
                        analisis.syncAttempts,
                        resolucion.motivo(),
                        null,
                    )
                    bloqueados++
                }

                else -> Unit // sigue esperando al padre, sin novedad
            }
        }

        lotesLocal.obtenerEnEsperaDeDependencia(usuarioId).forEach { lote ->
            when (val resolucion = resolverRegistrosDelLote(lote.uuidCliente)) {
                is ResolucionLote.Resuelta -> {
                    lotesLocal.actualizarEstadoSync(lote.uuidCliente, SyncStatus.PENDING, lote.syncAttempts, null, null)
                    promovidos++
                }

                is ResolucionLote.Bloqueada -> {
                    if (resolucion.causa == Resolucion.PadreSincronizadoSinId) {
                        lotesLocal.actualizarEstadoSync(
                            lote.uuidCliente,
                            SyncStatus.PENDING_DEPENDENCY,
                            lote.syncAttempts,
                            resolucion.causa.motivo(),
                            null,
                        )
                        bloqueados++
                    }
                }
            }
        }

        return Promocion(promovidos, bloqueados)
    }

    // ---------------------------------------------------------------------------------------------
    // Catálogos
    // ---------------------------------------------------------------------------------------------

    private suspend fun refrescarCatalogos(): ResultadoCatalogos {
        return when (val respuesta = apiClient.get<CambiosResponse>(Endpoints.SYNC_CAMBIOS)) {
            is ApiResult.Error -> ResultadoCatalogos(
                error = respuesta.error,
                sesionInvalida = respuesta.error as? ApiError.NoAutorizado,
            )

            is ApiResult.Exito -> {
                val cambios = respuesta.datos
                val cuando = ahora()
                catalogosLocal.reemplazarProveedores(cambios.aProveedores(cuando))
                catalogosLocal.reemplazarUnidades(cambios.aUnidades(cuando))
                catalogosLocal.reemplazarMotivosObservacion(cambios.aMotivosObservacion(cuando))
                catalogosLocal.reemplazarTiposQueso(cambios.aTiposQueso(cuando))
                catalogosLocal.reemplazarComunicados(cambios.aComunicados(cuando))
                catalogosLocal.reemplazarPredicciones(cambios.aPredicciones(cuando))
                catalogosLocal.reemplazarPrecioLitroVigente(cambios.precioLitroVigente, cuando)
                ResultadoCatalogos(actualizados = true)
            }
        }
    }

    private fun ahora(): LocalDateTime = ahoraComoFechaHora(reloj, zona)

    private fun escritorDe(
        marcar: (String, LocalDateTime) -> Unit,
        actualizar: (String, SyncStatus, Int, String?, LocalDateTime?) -> Unit,
    ) = EscritorDeEstado(marcar, actualizar)
}

// -------------------------------------------------------------------------------------------------
// Tipos internos
// -------------------------------------------------------------------------------------------------

/** Lo mínimo que el envío genérico necesita de una fila, sin importar de qué recurso venga. */
private data class ItemEnvio(val uuidCliente: String, val intentos: Int)

/** Las dos escrituras de estado que el motor hace sobre cualquiera de los 4 `LocalDataSource`. */
private class EscritorDeEstado(
    val marcar: (uuidCliente: String, sincronizadoEn: LocalDateTime) -> Unit,
    val actualizar: (uuidCliente: String, estado: SyncStatus, intentos: Int, error: String?, proximo: LocalDateTime?) -> Unit,
)

private data class ResultadoRecurso(
    val resumen: ResumenRecurso,
    val sesionInvalida: ApiError? = null,
)

private data class ResultadoCatalogos(
    val actualizados: Boolean = false,
    val error: ApiError? = null,
    val sesionInvalida: ApiError? = null,
)

private data class Promocion(val promovidos: Int, val bloqueados: Int)

private sealed interface Resolucion {
    data class Resuelta(val registroAcopioId: String) : Resolucion
    data object PadreSinSincronizar : Resolucion
    data object PadreSincronizadoSinId : Resolucion
    data object PadreInexistente : Resolucion
}

private sealed interface ResolucionLote {
    data class Resuelta(val registroAcopioIds: List<String>) : ResolucionLote
    data class Bloqueada(val causa: Resolucion) : ResolucionLote
}

/**
 * El texto que queda en `sync_error`. §6.1 pide que la UI muestre la espera por dependencia distinta de un
 * error ("esperando que se sincronice la entrega asociada", no "falló"), así que el motivo se escribe
 * pensando en que la pantalla de pendientes (Fase 7) lo muestre literal.
 */
private fun Resolucion.motivo(): String? = when (this) {
    is Resolucion.Resuelta -> null
    Resolucion.PadreSinSincronizar -> "Esperando que se sincronice la entrega de acopio asociada."
    Resolucion.PadreSincronizadoSinId ->
        "La entrega de acopio asociada ya se sincronizó, pero el servidor no devuelve su id en la " +
            "respuesta del lote (DATA-014): no se puede enviar hasta que el backend lo soporte (§18.1)."
    Resolucion.PadreInexistente -> "No se encontró en este dispositivo la entrega de acopio asociada."
}
