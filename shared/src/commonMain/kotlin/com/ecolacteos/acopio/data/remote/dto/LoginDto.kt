package com.ecolacteos.acopio.data.remote.dto

import kotlinx.serialization.Serializable

/** `POST /api/auth/login` -- único endpoint público de toda la API (`MOBILE_DATA_MAPPING.md §5.1`). */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val rol: Rol,
    val nombre: String,
    val expiraEnSegundos: Long,
)
