package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import com.ecolacteos.acopio.data.remote.serializer.LocalDateSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * `POST /api/recepcion-planta` (ONLINE-ONLY, `MOBILE_DATA_MAPPING.md §5.9`). `turno` es opcional en el
 * Request (default server-side `"UNICO"`) pero siempre presente en el Response -- no confundir los dos
 * lados. Sin `uuidCliente` (`DATA-006`): un duplicado devuelve `409 Conflicto`, no el patrón "devolver el
 * existente" de los 4 recursos offline-first.
 */
@Serializable
data class RecepcionPlantaRequest(
    @Serializable(with = LocalDateSerializer::class) val fecha: LocalDate,
    val turno: String? = null,
    val unidadId: String,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosCampo: Decimal,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosPlanta: Decimal,
)

/**
 * `diferenciaPct` y `litrosRegistradosAcopio` son releídos con `entityManager.refresh()` / `SUM()` --
 * el segundo es nullable (`SUM()` sobre cero filas da `NULL`), el primero en la práctica siempre presente.
 * `estado` es 100% calculado server-side.
 */
@Serializable
data class RecepcionPlantaResponse(
    val id: String,
    @Serializable(with = LocalDateSerializer::class) val fecha: LocalDate,
    val turno: String,
    val unidadId: String,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosCampo: Decimal,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosPlanta: Decimal,
    @Serializable(with = BigDecimalEscala2Serializer::class) val diferenciaPct: Decimal,
    val estado: EstadoConciliacion,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosRegistradosAcopio: Decimal? = null,
)
