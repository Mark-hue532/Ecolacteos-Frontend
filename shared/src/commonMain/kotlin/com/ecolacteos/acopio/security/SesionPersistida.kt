package com.ecolacteos.acopio.security

import com.ecolacteos.acopio.data.remote.dto.Rol
import com.ecolacteos.acopio.network.jsonApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

/**
 * Lo único que vive en almacenamiento seguro nativo (`PROMPT_FASE_03.md §1`, `MOBILE_ARCHITECTURE.md §4`).
 *
 * ❌ La contraseña **nunca** aparece acá, ni cifrada: no hay ningún campo donde pueda entrar. Si en algún
 * momento alguien agrega uno, rompe el test que verifica esto sobre el JSON serializado
 * (`GestorSesionTest`), a propósito.
 */
@Serializable
data class SesionPersistida(
    val token: String,
    val rol: Rol,
    val nombre: String,
    val usuarioId: String,
    val expiraEnEpochMillis: Long,
)

/**
 * (De)serialización de [SesionPersistida] a JSON, compartida por los tres `actual` de
 * [SecureTokenStorage] -- cada uno solo decide cómo cifrar/descifrar el string, no cómo tiene forma la
 * sesión. Reusa [jsonApi] (la misma config de todo el proyecto) en vez de crear una instancia de `Json`
 * paralela.
 */
internal fun SesionPersistida.aJson(): String = jsonApi.encodeToString(this)

/**
 * `null` ante cualquier JSON inválido o con forma inesperada -- un ciphertext corrupto o de una versión
 * vieja del esquema no debe crashear, se trata como "no hay sesión" (mismo criterio que un token de JWT
 * malformado, ver `JwtUsuarioId.kt`).
 */
internal fun String.aSesionPersistida(): SesionPersistida? = try {
    jsonApi.decodeFromString(SesionPersistida.serializer(), this)
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}
