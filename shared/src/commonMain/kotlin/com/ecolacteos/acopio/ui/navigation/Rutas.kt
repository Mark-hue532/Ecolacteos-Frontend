package com.ecolacteos.acopio.ui.navigation

/**
 * Los destinos de la app. La Fase 7 dejó los 7 comunes/VENTAS; la Fase 8A agrega los 6 de ACOPIADOR
 * (`A-01`..`A-06`) -- `A-07` va en `8B`. El resto del inventario (33 pantallas) se agrega en las demás
 * sub-fases -- esta fase no declara destinos sin pantalla (`PROMPT_FASE_07.md §3`: "no inventes media
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

    // Fase 8A -- ACOPIADOR (MOBILE_SCREENS.md §5).
    const val ACOPIO_RUTA = "acopio_ruta"
    const val ACOPIO_ESCANEAR = "acopio_escanear"
    const val ACOPIO_BUSCAR = "acopio_buscar"

    const val ARG_PROVEEDOR_ID = "proveedorId"
    private const val ACOPIO_REGISTRAR_BASE = "acopio_registrar"
    const val ACOPIO_REGISTRAR = "$ACOPIO_REGISTRAR_BASE/{$ARG_PROVEEDOR_ID}"
    fun acopioRegistrar(proveedorId: String) = "$ACOPIO_REGISTRAR_BASE/$proveedorId"

    private const val ACOPIO_HISTORIAL_BASE = "acopio_historial"
    const val ACOPIO_HISTORIAL = "$ACOPIO_HISTORIAL_BASE/{$ARG_PROVEEDOR_ID}"
    fun acopioHistorial(proveedorId: String) = "$ACOPIO_HISTORIAL_BASE/$proveedorId"

    const val ARG_REGISTRO_ID = "id"
    private const val ACOPIO_DETALLE_BASE = "acopio_detalle"
    const val ACOPIO_DETALLE = "$ACOPIO_DETALLE_BASE/{$ARG_REGISTRO_ID}"
    fun acopioDetalle(id: String) = "$ACOPIO_DETALLE_BASE/$id"
}
