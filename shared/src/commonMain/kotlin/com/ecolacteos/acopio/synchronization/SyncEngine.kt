package com.ecolacteos.acopio.synchronization

import com.ecolacteos.acopio.core.ApiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/** Los 4 recursos offline-first que este motor sincroniza (`MOBILE_ARCHITECTURE.md §12`). */
enum class RecursoSync {
    REGISTRO_ACOPIO,
    ANALISIS_CALIDAD,
    LOTE_PRODUCCION,
    VENTA,
}

/** Estado global del motor, para que la UI (Fase 7) muestre un indicador sin sondear. */
enum class EstadoSync {
    INACTIVO,
    SINCRONIZANDO,
}

/**
 * Qué pasó con un recurso en un ciclo. [sinRespuesta] es el caso defensivo de §6.2: `uuidCliente` propios
 * que el backend no devolvió ni en `confirmados[]` ni en `errores[]` -- quedan `SYNCING` y se reintentan,
 * **nunca** se asumen exitosos.
 */
data class ResumenRecurso(
    val enviados: Int = 0,
    val confirmados: Int = 0,
    val fallidosPermanentes: Int = 0,
    val fallidosTransitorios: Int = 0,
    val sinRespuesta: Int = 0,
    val enEsperaDeDependencia: Int = 0,
    val fragmentosEnviados: Int = 0,
)

/**
 * Resultado de un ciclo completo. [promovidosPorDependencia] cuenta las filas que pasaron de
 * `PENDING_DEPENDENCY` a `PENDING` **en este mismo ciclo** (§6.1); [bloqueadosPorIdDePadre] cuenta las que
 * no pudieron promoverse porque su padre ya está `SYNCED` pero el backend nunca devolvió su id
 * (`DATA-014`) -- ese contador es el que hace visible el gap en vez de dejarlo como una espera silenciosa.
 */
data class ResumenCiclo(
    val porRecurso: Map<RecursoSync, ResumenRecurso> = emptyMap(),
    val promovidosPorDependencia: Int = 0,
    val bloqueadosPorIdDePadre: Int = 0,
    val catalogosActualizados: Boolean = false,
    val errorDeCatalogos: ApiError? = null,
)

/**
 * Resultado de [SyncEngine.ejecutarCiclo]. La distinción que pide `PROMPT_FASE_05.md §2` para que la Fase 6
 * pueda separar "reintentar" de "volver a loguear" es entre [Completado] (haya habido fallos por ítem o no,
 * el ciclo corrió y el motor va a reintentar solo) y [SesionInvalida] (nada que reintentar hasta que el
 * usuario se loguee de nuevo).
 */
sealed interface ResultadoCiclo {

    /** El ciclo corrió entero. Los fallos por ítem viven en [resumen], no acá: son estado normal del motor. */
    data class Completado(val resumen: ResumenCiclo) : ResultadoCiclo

    /** No hay sesión activa: nada que sincronizar. No es un error, es el estado de una app deslogueada. */
    data object SinSesion : ResultadoCiclo

    /**
     * 401 con refresh ya fallido (el `ApiClient` de Fase 2/3 lo intenta antes de que la respuesta llegue
     * acá -- este motor **no** reimplementa refresh, trampa #3). Aborta el ciclo entero: reintentar sin
     * credenciales nuevas solo quema batería. Las filas que quedaron `SYNCING` se reintentan solas en el
     * primer ciclo posterior al re-login, sin perder nada (idempotencia por `uuidCliente`, §6.4).
     */
    data class SesionInvalida(val error: ApiError) : ResultadoCiclo

    /**
     * Ya había un ciclo corriendo. Se descarta el disparo en vez de encolarlo: dos ciclos concurrentes
     * reenviarían las mismas filas `SYNCING` (inofensivo por idempotencia, pero desperdicia datos móviles
     * en campo, que es justo lo que este diseño cuida).
     */
    data object YaEnCurso : ResultadoCiclo
}

/**
 * Motor de sincronización (`MOBILE_ARCHITECTURE.md §6`). Stateless entre invocaciones e interrumpible en
 * cualquier punto (§6.7): todo el estado vive en SQLite, así que si el proceso muere a mitad de ciclo el
 * próximo arranque continúa sin lógica de recuperación especial.
 *
 * El *scheduling* real (`WorkManager`/`BGTaskScheduler`) es Fase 9. Acá el ciclo se dispara solo por dos
 * vías: [observarConectividad] (transición a conectado) y una llamada directa a [ejecutarCiclo].
 */
interface SyncEngine {

    val estado: StateFlow<EstadoSync>

    /** Un ciclo completo: los 4 recursos en orden + promoción de dependencias + `/sync/cambios`. */
    suspend fun ejecutarCiclo(): ResultadoCiclo

    /**
     * Dispara un ciclo cada vez que [ConnectivityObserver] pasa a conectado (§6.5) -- incluido el primer
     * valor si ya arranca conectado, que cubre el "sincronizar al abrir la app" de §6.7. Devuelve el [Job]
     * para que quien lo lance (Fase 6/9) pueda cancelarlo con su propio ciclo de vida.
     */
    fun observarConectividad(scope: CoroutineScope): Job
}
