package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.data.remote.serializer.EnumConReservaSerializer
import kotlinx.serialization.Serializable

/** `AlertaAnomaliaResponse.tipo`, calculado 100% server-side por `AnomaliaService`. */
@Serializable(with = TipoAlerta.Serializer::class)
enum class TipoAlerta {
    VOLUMEN_ATIPICO,
    RIESGO_ADULTERACION,
    UNKNOWN,
    ;

    object Serializer : EnumConReservaSerializer<TipoAlerta>(
        serialName = "com.ecolacteos.acopio.TipoAlerta",
        valores = TipoAlerta.entries.toTypedArray(),
        reserva = TipoAlerta.UNKNOWN,
    )
}
