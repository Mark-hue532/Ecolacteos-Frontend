package com.ecolacteos.acopio.domain

import com.ecolacteos.acopio.core.ApiResult
import kotlinx.coroutines.flow.StateFlow

/** Resultado de pedir el cierre de sesión -- ver [GestorSesion.cerrarSesion]. */
enum class ResultadoCierreSesion {
    /** Se borró `SecureTokenStorage`. No hay más que limpiar todavía (sin caches, `PROMPT_FASE_03.md §6`). */
    CERRADA,

    /**
     * Bloqueado por trabajo sin sincronizar (C-09). El token **no** se tocó -- la UI de la Fase 7
     * (`MOBILE_SCREENS.md §4 S-07`) es quien decide qué ofrecer a partir de acá (sincronizar ahora, ver
     * pendientes, o un cierre forzado que hoy no existe: depende de las tablas `*_local` de la Fase 4 y de
     * limpiar caches personales de la Fase 6, ver checkpoint).
     */
    BLOQUEADA_POR_PENDIENTES,
}

/**
 * Gestión de la sesión del usuario (`PROMPT_FASE_03.md §5`) -- login, vigencia, refresh proactivo y
 * logout. Es la única clase de `shared/domain/` que ve tanto [com.ecolacteos.acopio.network.ApiClient]
 * como [com.ecolacteos.acopio.security.SecureTokenStorage] para esta responsabilidad, igual que un
 * `Repository` (`CLAUDE.md §3.4`) -- se llama distinto porque no hay origen local en SQLite todavía
 * (Fase 4), solo el almacenamiento seguro.
 */
interface GestorSesion {

    /**
     * Emite la sesión vigente o `null`, para que la UI de la Fase 7 reaccione (ej. a un logout o a un
     * 401) sin tener que sondear (`PROMPT_FASE_03.md §5`). No es un reloj: no emite un nuevo valor solo
     * porque haya pasado el tiempo de expiración mientras la app está abierta -- eso se resuelve
     * consultando [estaVigente] o [sesionActual] cuando haga falta (ej. al volver a foreground), igual que
     * hace `S-01 Splash`.
     */
    val sesion: StateFlow<Sesion?>

    /** `POST /api/auth/login`, decodifica `usuarioId` del JWT, calcula la expiración absoluta y persiste. */
    suspend fun iniciarSesion(email: String, password: String): ApiResult<Sesion>

    /** Lee la sesión de [com.ecolacteos.acopio.security.SecureTokenStorage], o `null` si no hay ninguna. */
    suspend fun sesionActual(): Sesion?

    /** `true` si hay sesión y su expiración todavía no pasó, según el reloj del dispositivo. */
    suspend fun estaVigente(): Boolean

    /**
     * Si quedan menos de 30 minutos de vigencia, llama a `POST /api/auth/refresh`. Un token ya expirado
     * **no** se intenta refrescar -- `/api/auth/refresh` exige el JWT todavía vigente
     * (`MOBILE_ARCHITECTURE.md §4`); expirado, el único camino es login nuevo.
     *
     * Un fallo acá (red, timeout, lo que sea) **no es fatal** ni invalida la sesión existente: se
     * propaga como [ApiResult.Error] para que quien llama decida si loguearlo, pero la sesión actual
     * sigue intacta -- sin conectividad, el usuario sigue pudiendo capturar offline.
     */
    suspend fun refrescarSiHaceFalta(): ApiResult<Unit>

    /**
     * Política de logout (C-09, `PROMPT_FASE_03.md §6`): bloqueado si [VerificadorPendientes] devuelve
     * `true`. Si no bloquea, borra `SecureTokenStorage` -- nada más, todavía no hay caches que limpiar.
     */
    suspend fun cerrarSesion(): ResultadoCierreSesion

    /**
     * Limpia la sesión sin pasar por la política de logout: se llama cuando el backend devuelve 401 en
     * cualquier request (`PROMPT_FASE_03.md §7`), no cuando el usuario lo pide. Un 401 significa que la
     * sesión ya no sirve del lado del servidor (expiró, o ADMIN desactivó al usuario) -- eso no es una
     * decisión que el usuario tome ni algo que la política de "hay trabajo sin sincronizar" deba bloquear:
     * las filas `*_local` sin sincronizar (cuando existan, Fase 4) siguen intactas en el dispositivo tal
     * cual quedaron, listas para subirse cuando este mismo usuario vuelva a loguearse.
     */
    suspend fun invalidarSesion()
}
