package com.ecolacteos.acopio.domain.model

import kotlinx.datetime.LocalDateTime

/**
 * Resultado de `POST /api/comunicados/{id}/confirmaciones` (`PROMPT_FASE_06.md §4.4`, ONLINE-ONLY,
 * `DATA-005`/`§18.2`: no idempotente, no tiene tabla local -- `§11.3`). Ver [CorreccionRegistro] -- mismo
 * motivo, `ComunicadoConfirmacionRepository` nunca devuelve el DTO crudo.
 */
data class ComunicadoConfirmacion(
    val id: String,
    val proveedorId: String,
    val proveedorNombre: String,
    val acopiadorId: String,
    val acopiadorNombre: String,
    val confirmadoEn: LocalDateTime,
)
