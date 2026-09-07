package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.Severidad
import com.ecolacteos.acopio.data.remote.dto.TipoAlerta
import kotlinx.datetime.LocalDateTime

/**
 * `AlertaAnomalia` (`PROMPT_FASE_08E.md §4`/§6`, ONLINE-ONLY, CALIDAD). Sin tabla local -- `§11.3`.
 * [zScore] nullable, **3 decimales** (`NUMERIC(6,3)`, trampa #6) -- nulo se omite, nunca `0.000`.
 */
data class AlertaAnomalia(
    val id: String,
    val registroAcopioId: String,
    val proveedorId: String,
    val proveedorNombre: String,
    val tipo: TipoAlerta,
    val zScore: Decimal?,
    val severidad: Severidad,
    val creadoEn: LocalDateTime,
)
