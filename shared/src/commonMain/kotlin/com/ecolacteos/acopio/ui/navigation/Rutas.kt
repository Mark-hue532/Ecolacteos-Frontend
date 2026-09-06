package com.ecolacteos.acopio.ui.navigation

/**
 * Los 7 destinos de esta fase (`MOBILE_SCREENS.md §2`). El resto del inventario (33 pantallas) se agrega en
 * la Fase 8 -- esta fase no declara destinos sin pantalla (`PROMPT_FASE_07.md §3`: "no inventes media
 * pantalla de paso").
 */
object Rutas {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val ESTADO_SINCRONIZACION = "estado_sincronizacion"
    const val VENTAS_HOME = "ventas_home"
    const val VENTAS_REGISTRAR = "ventas_registrar"
    private const val VENTAS_DETALLE_BASE = "ventas_detalle"
    const val ARG_UUID_CLIENTE = "uuidCliente"
    const val VENTAS_DETALLE = "$VENTAS_DETALLE_BASE/{$ARG_UUID_CLIENTE}"

    fun ventasDetalle(uuidCliente: String) = "$VENTAS_DETALLE_BASE/$uuidCliente"
}
