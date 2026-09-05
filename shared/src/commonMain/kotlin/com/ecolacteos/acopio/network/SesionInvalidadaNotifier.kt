package com.ecolacteos.acopio.network

/**
 * Punto de indirección para que [ApiClient] pueda avisar "el servidor devolvió 401, la sesión ya no sirve"
 * sin depender de `domain.GestorSesion` directamente -- eso cerraría un ciclo en el grafo de Koin:
 * `GestorSesion` necesita [ApiClient] para llamar login/refresh, así que si [ApiClient] necesitara
 * `GestorSesion` para el aviso de 401, ninguno de los dos se podría terminar de construir primero.
 *
 * El wiring de Koin (`di/SecurityModule.kt`) registra el listener real una vez armado el grafo completo,
 * llamando [registrar] con `GestorSesion.invalidarSesion` -- ni [ApiClient] ni este archivo conocen
 * `GestorSesion` en ningún momento.
 */
class SesionInvalidadaNotifier {
    private var alInvalidarse: (suspend () -> Unit)? = null

    /** Reemplaza el listener actual. Solo lo llama el wiring de Koin, una vez, al construir el grafo. */
    fun registrar(accion: suspend () -> Unit) {
        alInvalidarse = accion
    }

    internal suspend fun emitir() {
        alInvalidarse?.invoke()
    }
}
