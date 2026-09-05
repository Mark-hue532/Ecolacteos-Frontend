package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.data.remote.serializer.EnumConReservaSerializer
import kotlinx.serialization.Serializable

/** `AlertaAnomaliaResponse.severidad`, calculado 100% server-side por `AnomaliaService`. */
@Serializable(with = Severidad.Serializer::class)
enum class Severidad {
    BAJA,
    MEDIA,
    ALTA,
    UNKNOWN,
    ;

    object Serializer : EnumConReservaSerializer<Severidad>(
        serialName = "com.ecolacteos.acopio.Severidad",
        valores = Severidad.entries.toTypedArray(),
        reserva = Severidad.UNKNOWN,
    )
}
