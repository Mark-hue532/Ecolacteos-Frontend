package com.ecolacteos.acopio.domain.model

/**
 * Qué endpoint pobló una fila de `registro_acopio_cache` (`MOBILE_ARCHITECTURE.md §11.2`, C-04): el
 * listado resumen (`GET /registros-acopio/proveedor/{id}`, `RegistroAcopioResumenResponse`, **sin**
 * `uuidCliente`, `DATA-013`) o el detalle (`GET /registros-acopio/{id}`, con todo). Una fila `RESUMEN`
 * sirve igual para el caso de uso principal -- resolver el `server_id` de un padre ajeno -- porque `id` es
 * justo lo que necesita el request; el detalle solo hace falta si la UI quiere mostrar proveedor/observación.
 *
 * Este enum no viaja en ningún DTO de red (lo asigna el propio cliente al escribir la fila, según qué
 * endpoint la originó), pero `PROMPT_FASE_04.md §3` lo agrupa junto a los enums remotos (`TipoClienteVenta`,
 * `CicloCapital`) para efectos del `ColumnAdapter`: mismo criterio defensivo -- una fila cacheada por una
 * versión anterior/posterior de la app con un valor de `origen` que esta versión no reconoce no debe
 * romper la lectura completa de la tabla. De ahí el valor de reserva [UNKNOWN].
 */
enum class Origen {
    RESUMEN,
    DETALLE,
    UNKNOWN,
}
