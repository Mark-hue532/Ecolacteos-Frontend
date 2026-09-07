package com.ecolacteos.acopio.domain.model

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.EstadoConciliacion
import kotlinx.datetime.LocalDate

/**
 * `RecepcionPlanta` (`PROMPT_FASE_08E.md §4`, ONLINE-ONLY, sin tabla local -- `MOBILE_ARCHITECTURE.md
 * §11.3`). Existe para que `RecepcionPlantaRepository` nunca devuelva el DTO directo (`CLAUDE.md §3.4`).
 * [diferenciaPct] es columna `GENERATED`, 100% servidor -- nunca se recalcula acá (trampa #11).
 * [litrosRegistradosAcopio] nullable a propósito: un `SUM()` sobre cero filas da `NULL`, no `0`
 * (`§10.1` regla 3) -- `null` significa "sin registros de acopio", no "cero litros".
 */
data class RecepcionPlanta(
    val id: String,
    val fecha: LocalDate,
    val turno: String,
    val unidadId: String,
    val litrosCampo: Decimal,
    val litrosPlanta: Decimal,
    val diferenciaPct: Decimal,
    val estado: EstadoConciliacion,
    val litrosRegistradosAcopio: Decimal?,
)
