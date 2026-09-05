package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala6Serializer
import com.ecolacteos.acopio.data.remote.serializer.LocalDateTimeSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * `POST /api/registros-acopio` (OFFLINE-FIRST, `MOBILE_DATA_MAPPING.md §5.2`). `uuidCliente` es la clave
 * de idempotencia -- va en el body, nunca en un header (`CLAUDE.md §3.3`).
 */
@Serializable
data class RegistroAcopioDTO(
    val uuidCliente: String,
    val proveedorId: String,
    val unidadId: String,
    @Serializable(with = LocalDateTimeSerializer::class) val fechaHora: LocalDateTime,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litros: Decimal,
    @Serializable(with = BigDecimalEscala6Serializer::class) val gpsLat: Decimal? = null,
    @Serializable(with = BigDecimalEscala6Serializer::class) val gpsLng: Decimal? = null,
    val motivoObservacionId: String? = null,
    val litrosPorVoz: Boolean? = null,
    // Sin endpoint de subida real detrás (DATA-008) -- el campo existe en el contrato, no en el backend.
    val fotoUrl: String? = null,
)

/**
 * `motivoObservacion` acá es la **descripción en texto**, no el id -- campo distinto de
 * `RegistroAcopioDTO.motivoObservacionId` pese al nombre parecido (NAME_MISMATCH documentado en §5.2).
 * `fotoUrl` no existe en este DTO aunque el Request lo acepte (`DATA-007`) -- no se agrega.
 */
@Serializable
data class RegistroAcopioResponse(
    val id: String,
    val uuidCliente: String,
    val proveedorId: String,
    val proveedorNombre: String,
    val unidadId: String,
    @Serializable(with = LocalDateTimeSerializer::class) val fechaHora: LocalDateTime,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litros: Decimal,
    @Serializable(with = BigDecimalEscala6Serializer::class) val gpsLat: Decimal? = null,
    @Serializable(with = BigDecimalEscala6Serializer::class) val gpsLng: Decimal? = null,
    val motivoObservacion: String? = null,
    val litrosPorVoz: Boolean,
    @Serializable(with = LocalDateTimeSerializer::class) val sincronizadoEn: LocalDateTime,
)

/**
 * Historial liviano (RF-TRA-01). **No trae `uuidCliente`** (`DATA-013`) -- no inventarlo. Tampoco trae
 * `proveedorId`/`proveedorNombre`: el listado ya es por proveedor (path param).
 */
@Serializable
data class RegistroAcopioResumenResponse(
    val id: String,
    @Serializable(with = LocalDateTimeSerializer::class) val fechaHora: LocalDateTime,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litros: Decimal,
    val tieneObservacion: Boolean,
)

/** `POST /api/registros-acopio/{id}/correcciones`. Sin `uuidCliente` (`DATA-004`, no idempotente hoy). */
@Serializable
data class CorreccionRegistroRequest(
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosCorregido: Decimal,
    val motivo: String? = null,
)

@Serializable
data class CorreccionRegistroResponse(
    val id: String,
    val registroAcopioId: String,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosAnterior: Decimal,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosCorregido: Decimal,
    val motivo: String? = null,
    val usuarioNombre: String,
    @Serializable(with = LocalDateTimeSerializer::class) val creadoEn: LocalDateTime,
)
