package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.domain.GestorSesion

/** A dónde entra `S-01 Splash` (`MOBILE_SCREENS.md §4`) -- nunca pide nada al usuario, nunca espera red. */
sealed interface DestinoInicial {
    data object Home : DestinoInicial
    data object Login : DestinoInicial

    /** Token presente pero ya vencido -- `S-02` muestra el aviso "tu sesión venció" (`§4`). */
    data object LoginSesionVencida : DestinoInicial
}

/**
 * `S-01` (`PROMPT_FASE_07.md §2.4`, §5): decide leyendo solo `SecureTokenStorage` (vía `GestorSesion`,
 * sin red) -- el refresh proactivo es responsabilidad de [RefrescarSesionUseCase], que el `ViewModel`
 * dispara aparte y sin esperar, para no acoplar la decisión de destino a si la red respondió o no
 * (`§5`: "el splash nunca bloquea esperando red").
 */
class DecidirDestinoInicialUseCase(private val gestorSesion: GestorSesion) {
    suspend operator fun invoke(): DestinoInicial {
        gestorSesion.sesionActual() ?: return DestinoInicial.Login
        return if (gestorSesion.estaVigente()) DestinoInicial.Home else DestinoInicial.LoginSesionVencida
    }
}
