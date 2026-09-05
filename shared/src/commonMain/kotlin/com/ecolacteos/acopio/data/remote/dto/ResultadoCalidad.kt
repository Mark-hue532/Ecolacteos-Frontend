package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.data.remote.serializer.EnumConReservaSerializer
import kotlinx.serialization.Serializable

/**
 * `AnalisisCalidadResponse.resultado` (`MOBILE_DATA_MAPPING.md §1.6`). Hoy solo `APROBADO`/`RECHAZADO`
 * tienen productor real (`AnalisisCalidadService`); `OBSERVADO` existe en el dominio y el `CHECK` de
 * `schema.sql` pero ningún código lo asigna todavía -- se modela igual, es un valor válido que puede
 * empezar a llegar sin aviso.
 */
@Serializable(with = ResultadoCalidad.Serializer::class)
enum class ResultadoCalidad {
    APROBADO,
    RECHAZADO,
    OBSERVADO,
    UNKNOWN,
    ;

    object Serializer : EnumConReservaSerializer<ResultadoCalidad>(
        serialName = "com.ecolacteos.acopio.ResultadoCalidad",
        valores = ResultadoCalidad.entries.toTypedArray(),
        reserva = ResultadoCalidad.UNKNOWN,
    )
}
