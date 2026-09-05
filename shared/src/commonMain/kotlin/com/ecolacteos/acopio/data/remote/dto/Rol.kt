package com.ecolacteos.acopio.data.remote.dto

import com.ecolacteos.acopio.data.remote.serializer.EnumConReservaSerializer
import kotlinx.serialization.Serializable

/** `LoginResponse.rol`, uno de los 6 roles fijos del sistema (`CLAUDE.md §1`). */
@Serializable(with = Rol.Serializer::class)
enum class Rol {
    ADMIN,
    ACOPIADOR,
    CALIDAD,
    PRODUCCION,
    VENTAS,
    RECEPCION,
    UNKNOWN,
    ;

    object Serializer : EnumConReservaSerializer<Rol>(
        serialName = "com.ecolacteos.acopio.Rol",
        valores = Rol.entries.toTypedArray(),
        reserva = Rol.UNKNOWN,
    )
}
