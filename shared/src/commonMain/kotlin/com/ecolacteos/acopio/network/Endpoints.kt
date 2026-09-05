package com.ecolacteos.acopio.network

/**
 * Los 34 endpoints MOBILE de `MOBILE_ARCHITECTURE.md §3.1`, en un solo lugar -- ningún otro archivo debe
 * tener un path de la API escrito como string literal (`PROMPT_FASE_02.md §4`).
 *
 * No hay paginación ni parámetro de orden en ningún endpoint (`MOBILE_DATA_MAPPING.md §7`) -- no agregar
 * ninguno acá aunque parezca conveniente.
 */
object Endpoints {

    // 1-2: Autenticación
    const val LOGIN = "/api/auth/login"
    const val REFRESH = "/api/auth/refresh"

    // 3-7: RegistroAcopio (OFFLINE-FIRST)
    const val REGISTROS_ACOPIO = "/api/registros-acopio"
    fun registroAcopioPorId(id: String) = "/api/registros-acopio/$id"
    fun registrosAcopioPorProveedor(proveedorId: String) = "/api/registros-acopio/proveedor/$proveedorId"
    fun correccionesDeRegistro(id: String) = "/api/registros-acopio/$id/correcciones"

    // 8-10: AnalisisCalidad (OFFLINE-FIRST)
    const val ANALISIS_CALIDAD = "/api/analisis-calidad"
    fun analisisCalidadPorFolio(folio: String) = "/api/analisis-calidad/folio/$folio"
    fun analisisCalidadPorRegistro(registroAcopioId: String) = "/api/analisis-calidad/registro/$registroAcopioId"

    // 11-13: RecepcionPlanta (ONLINE-ONLY)
    const val RECEPCION_PLANTA = "/api/recepcion-planta"
    fun recepcionPlantaPorId(id: String) = "/api/recepcion-planta/$id"

    // 14-16: LoteProduccion (OFFLINE-FIRST)
    const val LOTES_PRODUCCION = "/api/lotes-produccion"
    fun lotePorId(id: String) = "/api/lotes-produccion/$id"

    // 17-19: Venta (OFFLINE-FIRST)
    const val VENTAS = "/api/ventas"
    fun ventaPorId(id: String) = "/api/ventas/$id"

    // 20-21: Comunicados
    fun comunicadosPorZona(zonaId: String) = "/api/comunicados/zona/$zonaId"
    fun confirmarComunicado(id: String) = "/api/comunicados/$id/confirmaciones"

    // 22-26: Motor de sync
    const val SYNC_REGISTROS_ACOPIO = "/api/sync/registros-acopio"
    const val SYNC_ANALISIS_CALIDAD = "/api/sync/analisis-calidad"
    const val SYNC_LOTES_PRODUCCION = "/api/sync/lotes-produccion"
    const val SYNC_VENTAS = "/api/sync/ventas"
    const val SYNC_CAMBIOS = "/api/sync/cambios"

    // 27: Ruta de zona
    fun rutaDeZona(zonaId: String) = "/api/zonas/$zonaId/ruta"

    // 28-29: Proveedores
    const val PROVEEDORES_OPERATIVO = "/api/proveedores/operativo"
    fun proveedorPorQr(codigoQr: String) = "/api/proveedores/qr/$codigoQr"

    // 30-31: Pagos
    fun pagosPorProveedor(proveedorId: String) = "/api/pagos/proveedor/$proveedorId"
    fun pagoPorId(id: String) = "/api/pagos/$id"

    // 32-34: Innovación
    fun scoreConfianza(proveedorId: String) = "/api/innovacion/score/$proveedorId"
    fun prediccionProveedor(proveedorId: String) = "/api/innovacion/prediccion/$proveedorId"
    const val ALERTAS = "/api/innovacion/alertas"
}
