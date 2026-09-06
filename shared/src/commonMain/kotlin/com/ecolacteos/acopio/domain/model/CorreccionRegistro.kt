package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDateTime

/**
 * Resultado de `POST /api/registros-acopio/{id}/correcciones` (`PROMPT_FASE_06.md §4.4`, ONLINE-ONLY,
 * `DATA-004`/`§18.7`: no idempotente, no tiene tabla local -- `§11.3`). Existe para que
 * `CorreccionRegistroRepository` nunca devuelva `CorreccionRegistroResponse` (el DTO) directo -- `CLAUDE.md
 * §3.4`: un `ViewModel` nunca ve un DTO de `data/remote/dto/`.
 */
data class CorreccionRegistro(
    val id: String,
    val registroAcopioId: String,
    val litrosAnterior: Decimal,
    val litrosCorregido: Decimal,
    val motivo: String?,
    val usuarioNombre: String,
    val creadoEn: LocalDateTime,
)
