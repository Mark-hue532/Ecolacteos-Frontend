package com.ecolacteos.acopio.domain.model

import kotlinx.datetime.LocalDateTime

/**
 * Modelo de dominio de `proveedor_cache` (`MOBILE_ARCHITECTURE.md §11.2`). Se reemplaza completo en cada
 * `/sync/cambios` exitoso -- [actualizadoEn] es un campo puramente local (cuándo se escribió esta fila en
 * el dispositivo), no viaja en `ProveedorPublicoResponse` (Fase 2); lo fija quien llame a
 * `reemplazarTodo` (Fase 6+), esta capa no depende del reloj (ver checkpoint, "Decisiones tomadas").
 */
data class Proveedor(
    val id: String,
    val nombre: String,
    val zonaActualId: String?,
    val zonaActualNombre: String?,
    val codigoQr: String?,
    val actualizadoEn: LocalDateTime,
)
