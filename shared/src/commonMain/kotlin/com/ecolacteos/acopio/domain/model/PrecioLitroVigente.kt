package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio de `precio_litro_vigente_cache` (`MOBILE_ARCHITECTURE.md §11.2`, fila única).
 *
 * **Decisión de esta fase** (documentada en el checkpoint): "no hay fila" y "hay fila con `precio = NULL`"
 * se tratan como el mismo caso -- el backend tampoco los distingue (`DATA`: `.orElse(null)`). Por eso
 * [precio] acá es **no-nulo**: el `LocalDataSource` devuelve `PrecioLitroVigente?` y colapsa ambos casos
 * ("nunca se sincronizó esta fila" / "el servidor no tiene precio vigente configurado") en `null` a nivel
 * de función -- el llamador nunca necesita distinguir "tabla vacía" de "precio no configurado" (exactamente
 * lo que pide `PROMPT_FASE_04.md §5`). Cuando el objeto no es `null`, `precio` y `actualizadoEn` siempre
 * están presentes.
 */
data class PrecioLitroVigente(
    val precio: Decimal,
    val actualizadoEn: LocalDateTime,
)
