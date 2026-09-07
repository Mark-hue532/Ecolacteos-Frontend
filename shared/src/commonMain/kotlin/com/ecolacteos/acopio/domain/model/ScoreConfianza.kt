package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import kotlinx.datetime.LocalDate

/** `ScoreConfianza` (`PROMPT_FASE_08E.md §4`, ONLINE-ONLY, CALIDAD). Sin tabla local -- `§11.3`. */
data class ScoreConfianza(
    val proveedorId: String,
    val periodo: LocalDate,
    val score: Decimal,
    val componenteCalidad: Decimal,
    val componenteRegularidad: Decimal,
    val componenteAnomalias: Decimal,
)
