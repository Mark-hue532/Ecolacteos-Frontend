package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import kotlinx.datetime.LocalDate

/**
 * Detalle de una `Venta` para `V-03` (`MOBILE_SCREENS.md §8`, `PROMPT_FASE_07.md §2.4`). Distinto de
 * [Venta] (Fase 6) porque agrega dos campos que **nunca** existen en `venta_local` -- son de solo lectura
 * del servidor (`MOBILE_DATA_MAPPING.md §5.5`) y esta fase no los persiste localmente (ver checkpoint,
 * hallazgo nuevo): `tipoQuesoNombre` y, sobre todo, `total` (columna `GENERATED ALWAYS` de Postgres).
 *
 * [total]/[tipoQuesoNombre] son `null` cuando todavía no se pudo confirmar contra el servidor -- la venta
 * no sincronizó nunca ([serverId] `null`) o la llamada a `GET /api/ventas/{id}` falló (sin conexión, error).
 * `V-03` los muestra como "No disponible" (`§10.1` regla 3), **nunca** como un cálculo local
 * `cantidad × precioUnitario` (`§8`: "sin recalcular nunca").
 */
data class VentaDetalle(
    val uuidCliente: String,
    val serverId: String?,
    val fecha: LocalDate,
    val tipoCliente: TipoClienteVenta,
    val tipoQuesoId: String,
    val tipoQuesoNombre: String?,
    val cantidad: Int,
    val precioUnitario: Decimal,
    val total: Decimal?,
    val syncStatus: SyncStatus,
)
