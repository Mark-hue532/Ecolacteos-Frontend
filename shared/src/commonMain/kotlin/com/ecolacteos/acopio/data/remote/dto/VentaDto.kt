package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import com.ecolacteos.acopio.data.remote.serializer.LocalDateSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * `POST /api/ventas` (OFFLINE-FIRST, `MOBILE_DATA_MAPPING.md §5.5`). `tipoCliente` viaja como
 * [TipoClienteVenta] -- la UI debe restringirlo a un selector cerrado (`DATA-010`, ver también la nota en
 * `TipoClienteVenta.kt`), nunca texto libre.
 */
@Serializable
data class VentaRequest(
    val uuidCliente: String,
    @Serializable(with = LocalDateSerializer::class) val fecha: LocalDate,
    val tipoCliente: TipoClienteVenta,
    val tipoQuesoId: String,
    val cantidad: Int,
    @Serializable(with = BigDecimalEscala2Serializer::class) val precioUnitario: Decimal,
)

/** `total` es columna `GENERATED` de Postgres -- read-only, nunca se calcula ni se envía desde el cliente. */
@Serializable
data class VentaResponse(
    val id: String,
    @Serializable(with = LocalDateSerializer::class) val fecha: LocalDate,
    val tipoCliente: TipoClienteVenta,
    val tipoQuesoNombre: String,
    val cantidad: Int,
    @Serializable(with = BigDecimalEscala2Serializer::class) val precioUnitario: Decimal,
    @Serializable(with = BigDecimalEscala2Serializer::class) val total: Decimal,
)
