package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.Sesion

/**
 * `S-02` (`MOBILE_SCREENS.md §4`). Decisión de esta fase (`PROMPT_FASE_07.md §2.4`): `GestorSesion` ya
 * documenta que cuenta como capa de "Repository" para la sesión (ver su propio comentario en
 * `domain/GestorSesion.kt`: "es la única clase de `shared/domain/` que ve tanto `ApiClient` como
 * `SecureTokenStorage`... igual que un `Repository`"), así que un `ViewModel` no puede llamarlo directo sin
 * saltarse la capa de `UseCase` (`CLAUDE.md §3.4`). Este wrapper de una línea es esa capa -- no reimplementa
 * nada de `GestorSesionImpl` (login, decodificar JWT, persistir), solo lo expone donde la regla dura lo exige.
 */
class LoginUseCase(private val gestorSesion: GestorSesion) {
    suspend operator fun invoke(email: String, password: String): ApiResult<Sesion> =
        gestorSesion.iniciarSesion(email, password)
}
