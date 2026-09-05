package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.serializer.BigDecimalEscala2Serializer
import com.ecolacteos.acopio.data.remote.serializer.InstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * `GET /api/sync/cambios` (`MOBILE_DATA_MAPPING.md §5.6`). `generadoEn` es el **único** campo `Instant` de
 * todo el contrato MOBILE -- todos los demás timestamps del sistema son `LocalDateTime` (§1.4, `DATA-001`).
 * El query param `desde` existe pero está confirmado sin efecto en v1 -- no se implementa filtrado
 * incremental en el cliente (`ApiClient.get` ya soporta query params si algún día se activa).
 */
@Serializable
data class CambiosResponse(
    @Serializable(with = InstantSerializer::class) val generadoEn: Instant,
    val proveedores: List<ProveedorPublicoResponse>,
    @Serializable(with = BigDecimalEscala2Serializer::class) val precioLitroVigente: Decimal? = null,
    val comunicados: List<ComunicadoResponse>,
    val prediccionesProveedor: List<PrediccionProveedorResponse>,
    val motivosObservacion: List<MotivoObservacionResponse>,
    val tiposQueso: List<TipoQuesoResponse>,
    val unidades: List<UnidadResponse>,
)
