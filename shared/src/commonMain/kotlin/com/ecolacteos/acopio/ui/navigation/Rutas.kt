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

    // Fase 8C -- CALIDAD (MOBILE_SCREENS.md §6): C-01..C-04. C-05..C-08 van en 8E.
    const val CALIDAD_HOME = "calidad_home"

    // `C-02` necesita un proveedor de partida -- reusa `BuscarProveedorScreen` (`A-03`) con un destino
    // propio, ver `PROMPT_FASE_08C.md §4`.
    const val CALIDAD_BUSCAR_PROVEEDOR = "calidad_buscar_proveedor"

    private const val CALIDAD_SELECCIONAR_REGISTRO_BASE = "calidad_seleccionar_registro"
    const val CALIDAD_SELECCIONAR_REGISTRO = "$CALIDAD_SELECCIONAR_REGISTRO_BASE/{$ARG_PROVEEDOR_ID}"
    fun calidadSeleccionarRegistro(proveedorId: String) = "$CALIDAD_SELECCIONAR_REGISTRO_BASE/$proveedorId"

    // `C-03` -- exactamente uno de los dos query param no nulo (mismo invariante que `AnalisisCalidad`).
    const val ARG_REGISTRO_ACOPIO_UUID_CLIENTE = "registroAcopioUuidCliente"
    const val ARG_REGISTRO_ACOPIO_SERVER_ID = "registroAcopioServerId"
    private const val CALIDAD_REGISTRAR_ANALISIS_BASE = "calidad_registrar_analisis"
    const val CALIDAD_REGISTRAR_ANALISIS =
        "$CALIDAD_REGISTRAR_ANALISIS_BASE?$ARG_REGISTRO_ACOPIO_UUID_CLIENTE={$ARG_REGISTRO_ACOPIO_UUID_CLIENTE}" +
            "&$ARG_REGISTRO_ACOPIO_SERVER_ID={$ARG_REGISTRO_ACOPIO_SERVER_ID}"
    fun calidadRegistrarAnalisis(uuidCliente: String?, serverId: String?): String {
        val query = listOfNotNull(
            uuidCliente?.let { "$ARG_REGISTRO_ACOPIO_UUID_CLIENTE=$it" },
            serverId?.let { "$ARG_REGISTRO_ACOPIO_SERVER_ID=$it" },
        ).joinToString("&")
        return "$CALIDAD_REGISTRAR_ANALISIS_BASE?$query"
    }

    private const val CALIDAD_DETALLE_ANALISIS_BASE = "calidad_detalle_analisis"
    const val CALIDAD_DETALLE_ANALISIS = "$CALIDAD_DETALLE_ANALISIS_BASE/{$ARG_REGISTRO_ID}"
    fun calidadDetalleAnalisis(registroAcopioId: String) = "$CALIDAD_DETALLE_ANALISIS_BASE/$registroAcopioId"

    // Fase 8E -- CALIDAD (MOBILE_SCREENS.md §6): C-05, C-06, C-07, C-08.
    const val CALIDAD_BUSCAR_POR_FOLIO = "calidad_buscar_por_folio"

    private const val CALIDAD_REGISTRAR_CORRECCION_BASE = "calidad_registrar_correccion"
    const val CALIDAD_REGISTRAR_CORRECCION = "$CALIDAD_REGISTRAR_CORRECCION_BASE/{$ARG_REGISTRO_ID}"
    fun calidadRegistrarCorreccion(registroAcopioId: String) = "$CALIDAD_REGISTRAR_CORRECCION_BASE/$registroAcopioId"

    const val CALIDAD_ALERTAS_ANOMALIA = "calidad_alertas_anomalia"

    // `C-08` necesita un proveedor de partida, mismo criterio que `C-02` -- reusa `BuscarProveedorScreen`
    // con un destino propio (esta búsqueda nunca navega a `CALIDAD_SELECCIONAR_REGISTRO`).
    const val CALIDAD_BUSCAR_PROVEEDOR_SCORE = "calidad_buscar_proveedor_score"

    private const val CALIDAD_SCORE_CONFIANZA_BASE = "calidad_score_confianza"
    const val CALIDAD_SCORE_CONFIANZA = "$CALIDAD_SCORE_CONFIANZA_BASE/{$ARG_PROVEEDOR_ID}"
    fun calidadScoreConfianza(proveedorId: String) = "$CALIDAD_SCORE_CONFIANZA_BASE/$proveedorId"

    // Fase 8D -- PRODUCCION (MOBILE_SCREENS.md §7): P-01..P-04.
    const val PRODUCCION_HOME = "produccion_home"

    // `P-02` necesita un proveedor de partida, mismo criterio que `C-02` (`8C`) -- reusa `BuscarProveedorScreen`.
    const val PRODUCCION_BUSCAR_PROVEEDOR = "produccion_buscar_proveedor"

    private const val PRODUCCION_SELECCIONAR_REGISTROS_BASE = "produccion_seleccionar_registros"
    const val PRODUCCION_SELECCIONAR_REGISTROS = "$PRODUCCION_SELECCIONAR_REGISTROS_BASE/{$ARG_PROVEEDOR_ID}"
    fun produccionSeleccionarRegistros(proveedorId: String) = "$PRODUCCION_SELECCIONAR_REGISTROS_BASE/$proveedorId"

    // `P-03` -- selección múltiple de `P-02`, codificada como dos listas separadas por coma (los ids son
    // UUIDs/slugs sin comas -- ver checkpoint). `totalLitrosSeleccionado` es solo de ayuda (`§4.3`, nunca
    // autocompleta `litrosUsados`).
    const val ARG_REGISTRO_ACOPIO_UUID_CLIENTES = "registroAcopioUuidClientes"
    const val ARG_REGISTRO_ACOPIO_SERVER_IDS = "registroAcopioServerIds"
    const val ARG_TOTAL_LITROS_SELECCIONADO = "totalLitrosSeleccionado"
    private const val PRODUCCION_REGISTRAR_LOTE_BASE = "produccion_registrar_lote"
    const val PRODUCCION_REGISTRAR_LOTE =
        "$PRODUCCION_REGISTRAR_LOTE_BASE?$ARG_REGISTRO_ACOPIO_UUID_CLIENTES={$ARG_REGISTRO_ACOPIO_UUID_CLIENTES}" +
            "&$ARG_REGISTRO_ACOPIO_SERVER_IDS={$ARG_REGISTRO_ACOPIO_SERVER_IDS}" +
            "&$ARG_TOTAL_LITROS_SELECCIONADO={$ARG_TOTAL_LITROS_SELECCIONADO}"
    fun produccionRegistrarLote(uuidClientes: List<String>, serverIds: List<String>, totalLitrosTexto: String): String {
        val query = listOf(
            "$ARG_REGISTRO_ACOPIO_UUID_CLIENTES=${uuidClientes.joinToString(",")}",
            "$ARG_REGISTRO_ACOPIO_SERVER_IDS=${serverIds.joinToString(",")}",
            "$ARG_TOTAL_LITROS_SELECCIONADO=$totalLitrosTexto",
        ).joinToString("&")
        return "$PRODUCCION_REGISTRAR_LOTE_BASE?$query"
    }

    private const val PRODUCCION_DETALLE_LOTE_BASE = "produccion_detalle_lote"
    const val PRODUCCION_DETALLE_LOTE = "$PRODUCCION_DETALLE_LOTE_BASE/{$ARG_REGISTRO_ID}"
    fun produccionDetalleLote(id: String) = "$PRODUCCION_DETALLE_LOTE_BASE/$id"

    // Fase 8E -- RECEPCION (MOBILE_SCREENS.md §9): R-01..R-04. `R-01` es el de-facto Home del rol (no hay
    // pantalla propia de "home RECEPCION" en el inventario de 33).
    const val RECEPCION_REGISTRAR = "recepcion_registrar"

    private const val RECEPCION_RESULTADO_BASE = "recepcion_resultado"
    const val RECEPCION_RESULTADO = "$RECEPCION_RESULTADO_BASE/{$ARG_REGISTRO_ID}"
    fun recepcionResultado(id: String) = "$RECEPCION_RESULTADO_BASE/$id"

    const val RECEPCION_HISTORIAL = "recepcion_historial"

    // `R-04` necesita un proveedor de partida, mismo criterio que `C-02`/`C-08` -- reusa `BuscarProveedorScreen`.
    const val RECEPCION_BUSCAR_PROVEEDOR = "recepcion_buscar_proveedor"

    private const val RECEPCION_PAGOS_BASE = "recepcion_pagos"
    const val RECEPCION_PAGOS = "$RECEPCION_PAGOS_BASE/{$ARG_PROVEEDOR_ID}"
    fun recepcionPagos(proveedorId: String) = "$RECEPCION_PAGOS_BASE/$proveedorId"
}
