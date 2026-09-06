package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.domain.GestorSesion

/**
 * Refresh proactivo de `S-01` (`MOBILE_SCREENS.md §4`: "< 30 min y hay conexión → intentar
 * `POST /api/auth/refresh` en background, no bloquear"). `GestorSesionImpl.refrescarSiHaceFalta()` ya
 * decide sola si hace falta (umbral de 30 min) y ya absorbe el fallo de red sin invalidar la sesión --
 * este wrapper solo cruza la capa de `UseCase` que `CLAUDE.md §3.4` exige. El `ViewModel` la llama
 * fire-and-forget (`viewModelScope.launch`), sin esperar el resultado antes de navegar a `S-03`.
 */
class RefrescarSesionUseCase(private val gestorSesion: GestorSesion) {
    suspend operator fun invoke(): ApiResult<Unit> = gestorSesion.refrescarSiHaceFalta()
}
