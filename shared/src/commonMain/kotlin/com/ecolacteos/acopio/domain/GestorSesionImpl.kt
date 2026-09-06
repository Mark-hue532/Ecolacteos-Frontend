package com.ecolacteos.acopio.domain

import com.ecolacteos.acopio.core.ApiError
import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.core.flatMap
import com.ecolacteos.acopio.data.remote.dto.LoginRequest
import com.ecolacteos.acopio.data.remote.dto.LoginResponse
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.security.AlmacenamientoSeguroDeSesion
import com.ecolacteos.acopio.security.usuarioIdDesdeJwt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/** Umbral de refresh proactivo (`MOBILE_ARCHITECTURE.md §4`, `PROMPT_FASE_03.md §5`). */
private const val UMBRAL_REFRESH_MILLIS = 30 * 60 * 1000L

/**
 * [verificadorPendientes] es `Lazy` desde Fase 6, **a propósito** -- mismo motivo exacto que
 * [com.ecolacteos.acopio.network.TokenProviderSobreGestorSesion]: `VerificadorPendientes` ahora lo
 * implementa `VerificarPendientesUseCase` (`domain/usecase/`), que necesita los 4 `Repository` de
 * escritura, y cada uno de esos `Repository` necesita este mismo `GestorSesion` para resolver el
 * `usuarioId` de la sesión activa. Resolver `verificadorPendientes` de manera *ansiosa* en el constructor
 * reproduce el ciclo (`StackOverflowError` en Koin al armar el grafo, se ve en `CoreModuleTest`). Con
 * `Lazy`, recién se resuelve la primera vez que [cerrarSesion] se llama de verdad, momento en el que todo
 * el grafo ya terminó de armarse.
 */
class GestorSesionImpl(
    private val apiClient: ApiClient,
    private val almacenamiento: AlmacenamientoSeguroDeSesion,
    private val verificadorPendientes: Lazy<VerificadorPendientes>,
    private val reloj: Clock = Clock.System,
) : GestorSesion {

    private val _sesion = MutableStateFlow<Sesion?>(null)
    override val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()

    override suspend fun iniciarSesion(email: String, password: String): ApiResult<Sesion> =
        apiClient.post<LoginRequest, LoginResponse>(Endpoints.LOGIN, LoginRequest(email, password))
            .flatMap { respuesta -> respuesta.aSesionOError() }
            .also { resultado ->
                if (resultado is ApiResult.Exito) persistirYPublicar(resultado.datos)
            }

    override suspend fun sesionActual(): Sesion? {
        _sesion.value?.let { return it }
        val sesion = almacenamiento.leer()?.aSesion() ?: return null
        _sesion.value = sesion
        return sesion
    }

    override suspend fun estaVigente(): Boolean {
        val sesion = sesionActual() ?: return false
        return sesion.expiraEnEpochMillis > reloj.now().toEpochMilliseconds()
    }

    override suspend fun refrescarSiHaceFalta(): ApiResult<Unit> {
        val sesion = sesionActual() ?: return ApiResult.Exito(Unit) // sin sesion, nada que refrescar
        val ahora = reloj.now().toEpochMilliseconds()

        // Un token ya vencido no se puede refrescar -- /api/auth/refresh exige el JWT todavia vigente
        // (MOBILE_ARCHITECTURE.md §4). El unico camino desde aca es login nuevo, fuera del alcance de esta
        // funcion.
        if (sesion.expiraEnEpochMillis <= ahora) return ApiResult.Exito(Unit)

        val faltante = sesion.expiraEnEpochMillis - ahora
        if (faltante >= UMBRAL_REFRESH_MILLIS) return ApiResult.Exito(Unit) // vigencia de sobra

        return when (val resultado = apiClient.postSinBody<LoginResponse>(Endpoints.REFRESH)) {
            // Un fallo de red en el refresh no invalida la sesion existente (PROMPT_FASE_03.md §5): no se
            // toca ni el almacenamiento ni el Flow, se propaga el error para que quien llama decida si
            // loguearlo. Sin conectividad, el usuario sigue capturando offline con el token que ya tenia.
            is ApiResult.Error -> resultado
            is ApiResult.Exito -> {
                val nuevaSesion = sesion.copy(
                    token = resultado.datos.token,
                    rol = resultado.datos.rol,
                    nombre = resultado.datos.nombre,
                    expiraEnEpochMillis = reloj.now().toEpochMilliseconds() + resultado.datos.expiraEnSegundos * 1000,
                )
                persistirYPublicar(nuevaSesion)
                ApiResult.Exito(Unit)
            }
        }
    }

    override suspend fun cerrarSesion(): ResultadoCierreSesion {
        if (verificadorPendientes.value.hayTrabajoSinSincronizar()) return ResultadoCierreSesion.BLOQUEADA_POR_PENDIENTES
        limpiar()
        return ResultadoCierreSesion.CERRADA
    }

    override suspend fun invalidarSesion() {
        // A proposito, sin pasar por verificadorPendientes -- ver el comentario en GestorSesion.invalidarSesion.
        limpiar()
    }

    private suspend fun limpiar() {
        almacenamiento.borrar()
        _sesion.value = null
    }

    private suspend fun persistirYPublicar(sesion: Sesion) {
        almacenamiento.guardar(sesion.aSesionPersistida())
        _sesion.value = sesion
    }

    private fun LoginResponse.aSesionOError(): ApiResult<Sesion> {
        val usuarioId = usuarioIdDesdeJwt(token)
            ?: return ApiResult.Error(ApiError.Desconocido("El token de sesion no tiene un usuarioId valido"))
        val sesion = Sesion(
            token = token,
            usuarioId = usuarioId,
            rol = rol,
            nombre = nombre,
            expiraEnEpochMillis = reloj.now().toEpochMilliseconds() + expiraEnSegundos * 1000,
        )
        return ApiResult.Exito(sesion)
    }
}
