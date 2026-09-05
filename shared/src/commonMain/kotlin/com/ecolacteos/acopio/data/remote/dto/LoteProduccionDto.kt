package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import com.ecolacteos.acopio.data.remote.serializer.LocalDateSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * `POST /api/lotes-produccion` (OFFLINE-FIRST con dependencia, `MOBILE_DATA_MAPPING.md §5.4`).
 * `registroAcopioIds` tiene la misma indirección de `server_id` que `AnalisisCalidadRequest.registroAcopioId`.
 */
@Serializable
data class CrearLoteRequest(
    val uuidCliente: String,
    @Serializable(with = LocalDateSerializer::class) val fecha: LocalDate,
    val tipoQuesoId: String,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosUsados: Decimal,
    val unidadesObtenidas: Int,
    val registroAcopioIds: List<String>,
)

/** `rendimientoPct` es nullable: solo se calcula si `litrosUsados > 0` (NULLABILITY_NOTE, fácil de omitir). */
@Serializable
data class LoteProduccionResponse(
    val id: String,
    @Serializable(with = LocalDateSerializer::class) val fecha: LocalDate,
    val tipoQuesoNombre: String,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosUsados: Decimal,
    val unidadesObtenidas: Int,
    @Serializable(with = BigDecimalEscala2Serializer::class) val rendimientoPct: Decimal? = null,
    @Serializable(with = BigDecimalEscala2Serializer::class) val rendimientoEsperadoPct: Decimal,
    val registroAcopioIds: List<String>,
)
