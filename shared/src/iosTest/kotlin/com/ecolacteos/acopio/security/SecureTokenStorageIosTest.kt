package com.ecolacteos.acopio.security

import com.ecolacteos.acopio.data.remote.dto.Rol
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `SecureTokenStorage` real sobre Keychain (`PROMPT_FASE_03.md §8`): el simulador de iOS tiene Keychain
 * funcional, así que -- a diferencia de Android -- esto corre de verdad en CI (`iosSimulatorArm64Test`,
 * `.github/workflows/verificacion-ios.yml`), no es un test documentado-pero-sin-ejecutar.
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

    // El Keychain persiste entre tests dentro de la misma corrida del simulador -- limpiar en los dos
    // extremos para que un test no deje basura que afecte al siguiente.
    @AfterTest
    fun limpiar() = runTest {
        storage.borrar()
    }

    @Test
    fun `guardar y leer devuelve exactamente lo guardado`() = runTest {
        storage.borrar()

        storage.guardar(sesion)
        val leido = storage.leer()
        assertEquals(sesion, leido)
    }

    @Test
    fun `leer sin haber guardado nunca devuelve null`() = runTest {
        storage.borrar()

        assertNull(storage.leer())
    }

    @Test
    fun `leer despues de borrar devuelve null`() = runTest {
        storage.guardar(sesion)

        storage.borrar()

        assertNull(storage.leer())
    }

    @Test
    fun `guardar dos veces actualiza en vez de acumular entradas`() = runTest {
        storage.borrar()
        storage.guardar(sesion)

        val sesionActualizada = sesion.copy(token = "token-actualizado")
        storage.guardar(sesionActualizada)

        assertEquals(sesionActualizada, storage.leer())
    }
}
