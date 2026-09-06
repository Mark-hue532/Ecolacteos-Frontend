package com.ecolacteos.acopio.synchronization

import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.data.remote.dto.Rol
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.ResultadoCierreSesion
import com.ecolacteos.acopio.domain.Sesion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Fake de [ConnectivityObserver] (`PROMPT_FASE_05.md §4`): permite emitir `false`/`true` a voluntad sin
 * mockear nada de plataforma -- ni `ConnectivityManager` ni `NWPathMonitor` entran en estos tests.
 */
class ConnectivityObserverFake(inicial: Boolean = false) : ConnectivityObserver {
    private val estado = MutableStateFlow(inicial)
    override val conectado: Flow<Boolean> = estado.asStateFlow()

    fun emitir(conectado: Boolean) {
        estado.value = conectado
    }
}

/** Sesión fija; el motor solo consume `sesionActual()?.usuarioId`. `null` = app deslogueada. */
class GestorSesionFake(private var sesionFija: Sesion? = SESION_DE_PRUEBA) : GestorSesion {

    private val flujo = MutableStateFlow(sesionFija)
    override val sesion: StateFlow<Sesion?> = flujo.asStateFlow()

    override suspend fun iniciarSesion(email: String, password: String): ApiResult<Sesion> =
        error("no lo usa el SyncEngine")

    override suspend fun sesionActual(): Sesion? = sesionFija

    override suspend fun estaVigente(): Boolean = sesionFija != null

    override suspend fun refrescarSiHaceFalta(): ApiResult<Unit> = ApiResult.Exito(Unit)

    override suspend fun cerrarSesion(): ResultadoCierreSesion = ResultadoCierreSesion.CERRADA

    override suspend fun invalidarSesion() {
        sesionFija = null
        flujo.value = null
    }

    /**
     * Cambia la sesión activa sin pasar por [iniciarSesion] (que este fake no implementa) -- exclusivo del
     * escenario multiusuario de `PROMPT_FASE_06.md §6` punto 3: "usuario A con una fila PENDING, usuario B
     * loguea en el mismo dispositivo". Emite por [sesion] igual que un login real, para que los `Flow`
     * escopeados por `flatMapLatest` (`RegistroAcopioRepositoryImpl` et al.) reaccionen.
     */
    fun loguearComo(sesion: Sesion) {
        sesionFija = sesion
        flujo.value = sesion
    }

    companion object {
        const val USUARIO_ID = "usuario-1"
        const val USUARIO_ID_B = "usuario-2"

        val SESION_DE_PRUEBA = Sesion(
            token = "token-de-prueba",
            usuarioId = USUARIO_ID,
            rol = Rol.ACOPIADOR,
            nombre = "Ana",
            expiraEnEpochMillis = Long.MAX_VALUE,
        )

        /** Segunda sesión del mismo dispositivo (`PROMPT_FASE_06.md §6` punto 3, escenario multiusuario). */
        val SESION_DE_PRUEBA_B = Sesion(
            token = "token-de-prueba-b",
            usuarioId = USUARIO_ID_B,
            rol = Rol.ACOPIADOR,
            nombre = "Beto",
            expiraEnEpochMillis = Long.MAX_VALUE,
        )
    }
}

/** Reloj determinista: el backoff se afirma contra valores exactos, no contra "más o menos ahora". */
class RelojFijo(private var instante: Instant) : Clock {
    override fun now(): Instant = instante

    fun avanzar(duracion: Duration) {
        instante += duracion
    }
}
