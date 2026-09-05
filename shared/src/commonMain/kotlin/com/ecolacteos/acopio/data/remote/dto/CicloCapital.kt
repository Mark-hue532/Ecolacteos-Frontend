package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.data.remote.serializer.EnumConReservaSerializer
import kotlinx.serialization.Serializable

/** `TipoQuesoResponse.cicloCapital`. ADMIN define los 2 valores reales vía catálogo. */
@Serializable(with = CicloCapital.Serializer::class)
enum class CicloCapital {
    RAPIDO,
    MADURACION,
    UNKNOWN,
    ;

    object Serializer : EnumConReservaSerializer<CicloCapital>(
        serialName = "com.ecolacteos.acopio.CicloCapital",
        valores = CicloCapital.entries.toTypedArray(),
        reserva = CicloCapital.UNKNOWN,
    )
}
