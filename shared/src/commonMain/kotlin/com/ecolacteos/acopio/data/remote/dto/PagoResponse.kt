package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala3Serializer
import com.ecolacteos.acopio.data.remote.serializer.LocalDateSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Lectura móvil, RECEPCION (`MOBILE_DATA_MAPPING.md §5.10`). ⚠️ `precioLitro` tiene **3 decimales**
 * (`precision=6,scale=3`), distinto del resto de campos monetarios del contrato que usan 2 -- cuidado al
 * formatear en UI. `total` es columna `GENERATED`, read-only. Sin Request de creación en el alcance móvil
 * (`POST /api/pagos/generar` es WEB/ADMIN).
 */
@Serializable
data class PagoResponse(
    val id: String,
    val proveedorId: String,
    val proveedorNombre: String,
    @Serializable(with = LocalDateSerializer::class) val semanaInicio: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val semanaFin: LocalDate,
    @Serializable(with = BigDecimalEscala2Serializer::class) val litrosTotales: Decimal,
    @Serializable(with = BigDecimalEscala3Serializer::class) val precioLitro: Decimal,
    @Serializable(with = BigDecimalEscala2Serializer::class) val total: Decimal,
    val comprobanteGenerado: Boolean,
    val registroAcopioIds: List<String>,
)
