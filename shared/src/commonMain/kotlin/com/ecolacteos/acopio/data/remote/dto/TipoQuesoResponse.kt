package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import kotlinx.serialization.Serializable

/**
 * Nested DTO de `CambiosResponse.tiposQueso` (`MOBILE_DATA_MAPPING.md §5.6`). Dentro de `/sync/cambios`,
 * `activo` siempre llega `true` (filtro `findByActivoTrue()`), aunque el campo admite `false` en otros
 * contextos hipotéticos -- se modela como `Boolean` normal, sin asumir el valor fijo en el tipo.
 */
@Serializable
data class TipoQuesoResponse(
    val id: String,
    val nombre: String,
    @Serializable(with = BigDecimalEscala2Serializer::class) val rendimientoEsperadoPct: Decimal,
    val cicloCapital: CicloCapital,
    val activo: Boolean,
)
