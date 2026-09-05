package com.ecolacteos.acopio.domain

import com.ecolacteos.acopio.data.remote.dto.Rol
import com.ecolacteos.acopio.security.SesionPersistida

/**
 * Modelo de dominio de la sesión -- lo que ve [GestorSesion] y, más adelante, la UI de la Fase 7
 * (`CLAUDE.md §3.4`: un `ViewModel` nunca importa un DTO de `data/remote/dto`, y tampoco debería depender
 * de [SesionPersistida], que es un detalle de cómo se guarda, no de qué es una sesión).
 */
data class Sesion(
    val token: String,
    val usuarioId: String,
    val rol: Rol,
    val nombre: String,
    val expiraEnEpochMillis: Long,
)

internal fun SesionPersistida.aSesion(): Sesion = Sesion(
    token = token,
    usuarioId = usuarioId,
    rol = rol,
    nombre = nombre,
    expiraEnEpochMillis = expiraEnEpochMillis,
)

internal fun Sesion.aSesionPersistida(): SesionPersistida = SesionPersistida(
    token = token,
    rol = rol,
    nombre = nombre,
    usuarioId = usuarioId,
    expiraEnEpochMillis = expiraEnEpochMillis,
)
