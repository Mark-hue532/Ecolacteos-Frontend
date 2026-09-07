package com.ecolacteos.acopio.ui.navigation

/**
 * Los destinos de la app. La Fase 7 dejó los 7 comunes/VENTAS; la Fase 8A agregó los 6 de ACOPIADOR
 * (`A-01`..`A-06`); `8B` agrega `S-05`, `S-06`, `S-07` y `A-07`, y el modo edición de `A-04`/`V-02`
 * (`PROMPT_FASE_08B.md §7` decisión 1). El resto del inventario (33 pantallas) se agrega en las demás
 * sub-fases -- esta fase no declara destinos sin pantalla (`PROMPT_FASE_07.md §3`: "no inventes media
 * pantalla de paso").
 */
object Rutas {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val ESTADO_SINCRONIZACION = "estado_sincronizacion"
    const val VENTAS_HOME = "ventas_home"

    // `V-02` -- `8B` le agrega el modo edición vía query param opcional (mismo destino, no uno nuevo).
    const val ARG_UUID_CLIENTE_EDITAR = "uuidClienteEditar"
    private const val VENTAS_REGISTRAR_BASE = "ventas_registrar"
    const val VENTAS_REGISTRAR = "$VENTAS_REGISTRAR_BASE?$ARG_UUID_CLIENTE_EDITAR={$ARG_UUID_CLIENTE_EDITAR}"
    fun ventasRegistrar() = VENTAS_REGISTRAR_BASE
    fun ventasEditar(uuidCliente: String) = "$VENTAS_REGISTRAR_BASE?$ARG_UUID_CLIENTE_EDITAR=$uuidCliente"

    private const val VENTAS_DETALLE_BASE = "ventas_detalle"
    const val ARG_UUID_CLIENTE = "uuidCliente"
    const val VENTAS_DETALLE = "$VENTAS_DETALLE_BASE/{$ARG_UUID_CLIENTE}"

    fun ventasDetalle(uuidCliente: String) = "$VENTAS_DETALLE_BASE/$uuidCliente"

    // Fase 8A -- ACOPIADOR (MOBILE_SCREENS.md §5).
    const val ACOPIO_RUTA = "acopio_ruta"
    const val ACOPIO_ESCANEAR = "acopio_escanear"
    const val ACOPIO_BUSCAR = "acopio_buscar"

    // `A-04` -- `8B` le agrega el modo edición vía query param opcional (mismo destino, no uno nuevo):
    // `proveedorId` para crear, `uuidClienteEditar` para editar y reintentar desde `S-05`. Nunca los dos
    // a la vez -- `RegistrarAcopioViewModel` resuelve el `proveedorId` real desde la fila cuando edita.
    const val ARG_PROVEEDOR_ID = "proveedorId"
    private const val ACOPIO_REGISTRAR_BASE = "acopio_registrar"
    const val ACOPIO_REGISTRAR =
        "$ACOPIO_REGISTRAR_BASE?$ARG_PROVEEDOR_ID={$ARG_PROVEEDOR_ID}&$ARG_UUID_CLIENTE_EDITAR={$ARG_UUID_CLIENTE_EDITAR}"
    fun acopioRegistrar(proveedorId: String) = "$ACOPIO_REGISTRAR_BASE?$ARG_PROVEEDOR_ID=$proveedorId"
    fun acopioEditar(uuidCliente: String) = "$ACOPIO_REGISTRAR_BASE?$ARG_UUID_CLIENTE_EDITAR=$uuidCliente"

    private const val ACOPIO_HISTORIAL_BASE = "acopio_historial"
    const val ACOPIO_HISTORIAL = "$ACOPIO_HISTORIAL_BASE/{$ARG_PROVEEDOR_ID}"
    fun acopioHistorial(proveedorId: String) = "$ACOPIO_HISTORIAL_BASE/$proveedorId"

    const val ARG_REGISTRO_ID = "id"
    private const val ACOPIO_DETALLE_BASE = "acopio_detalle"
    const val ACOPIO_DETALLE = "$ACOPIO_DETALLE_BASE/{$ARG_REGISTRO_ID}"
    fun acopioDetalle(id: String) = "$ACOPIO_DETALLE_BASE/$id"

    // Fase 8B -- comunes (MOBILE_SCREENS.md §4) y A-07 (§5).
    const val PENDIENTES = "pendientes"
    const val COMUNICADOS = "comunicados"
    const val AJUSTES = "ajustes"

    const val ARG_COMUNICADO_ID = "comunicadoId"
    private const val ACOPIO_CONFIRMAR_COMUNICADO_BASE = "acopio_confirmar_comunicado"
    const val ACOPIO_CONFIRMAR_COMUNICADO = "$ACOPIO_CONFIRMAR_COMUNICADO_BASE/{$ARG_COMUNICADO_ID}"
    fun acopioConfirmarComunicado(comunicadoId: String) = "$ACOPIO_CONFIRMAR_COMUNICADO_BASE/$comunicadoId"
}
