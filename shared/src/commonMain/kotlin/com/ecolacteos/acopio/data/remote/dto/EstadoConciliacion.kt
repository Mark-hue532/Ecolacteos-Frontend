package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.data.remote.serializer.EnumConReservaSerializer
import kotlinx.serialization.Serializable

/** `RecepcionPlantaResponse.estado`, calculado 100% server-side por `ConciliacionService`. */
@Serializable(with = EstadoConciliacion.Serializer::class)
enum class EstadoConciliacion {
    OK,
    ALERTA,
    UNKNOWN,
    ;

    object Serializer : EnumConReservaSerializer<EstadoConciliacion>(
        serialName = "com.ecolacteos.acopio.EstadoConciliacion",
        valores = EstadoConciliacion.entries.toTypedArray(),
        reserva = EstadoConciliacion.UNKNOWN,
    )
}
