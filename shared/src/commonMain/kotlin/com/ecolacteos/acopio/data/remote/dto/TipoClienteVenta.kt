package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.data.remote.serializer.EnumConReservaSerializer
import kotlinx.serialization.Serializable

/**
 * `VentaRequest.tipoCliente` / `VentaResponse.tipoCliente`. En el backend es un `String` validado en
 * runtime vía `TipoClienteVenta.valueOf(...)`, no un enum real a nivel de Bean Validation (`DATA-010`):
 * un valor fuera de estos 3 provoca un `500` en el servidor, no un `400`. La UI de la app **debe** usar un
 * selector cerrado sobre estos 3 valores, nunca texto libre, para no poder producir ese caso.
 */
@Serializable(with = TipoClienteVenta.Serializer::class)
enum class TipoClienteVenta {
    MAYORISTA,
    PROVEEDOR,
    PUBLICO,
    UNKNOWN,
    ;

    object Serializer : EnumConReservaSerializer<TipoClienteVenta>(
        serialName = "com.ecolacteos.acopio.TipoClienteVenta",
        valores = TipoClienteVenta.entries.toTypedArray(),
        reserva = TipoClienteVenta.UNKNOWN,
    )
}
