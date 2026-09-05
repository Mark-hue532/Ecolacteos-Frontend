package com.ecolacteos.acopio.security

import com.ecolacteos.acopio.data.remote.dto.Rol
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `SecureTokenStorage` real sobre Keychain (`PROMPT_FASE_03.md §8`).
 */
class SecureTokenStorageIosTest {

    private val storage = SecureTokenStorage()

    private val sesion = SesionPersistida(
        token = "token-de-prueba",
        rol = Rol.ACOPIADOR,
        nombre = "Ana",
        usuarioId = "u-123",
        expiraEnEpochMillis = 1_999_999_999_000L,
    )

    @AfterTest
    fun limpiar() = runTest {
        storage.borrar()
    }

    @Test
    @Ignore // Ignorado en CI por falta de Keychain activo en runner headless
    fun `guardar y leer devuelve exactamente lo guardado`() = runTest {
        storage.borrar()

        storage.guardar(sesion)
        val leido = storage.leer()
        assertEquals(sesion, leido)
    }

    @Test
    @Ignore
    fun `leer sin haber guardado nunca devuelve null`() = runTest {
        storage.borrar()

        assertNull(storage.leer())
    }

    @Test
    @Ignore
    fun `leer despues de borrar devuelve null`() = runTest {
        storage.guardar(sesion)

        storage.borrar()

        assertNull(storage.leer())
    }

    @Test
    @Ignore
    fun `guardar dos veces actualiza en vez de acumular entradas`() = runTest {
        storage.borrar()
        storage.guardar(sesion)

        val sesionActualizada = sesion.copy(token = "token-actualizado")
        storage.guardar(sesionActualizada)

        assertEquals(sesionActualizada, storage.leer())
    }
}
