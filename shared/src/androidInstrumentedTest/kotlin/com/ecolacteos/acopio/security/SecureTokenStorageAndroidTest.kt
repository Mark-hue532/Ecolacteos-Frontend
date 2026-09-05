package com.ecolacteos.acopio.security

import androidx.test.platform.app.InstrumentationRegistry
import com.ecolacteos.acopio.data.remote.dto.Rol
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `SecureTokenStorage` real sobre Android Keystore (`PROMPT_FASE_03.md §8`).
 *
 * ⚠️ **No corre en este CI.** El Keystore necesita un dispositivo/emulador real; `verificacion-android.yml`
 * hoy solo compila y corre `jvmTest` (sin emulador). Este test queda **escrito pero sin ejecutar** -- así
 * se documenta en el checkpoint de la Fase 3, no se declara verificado.
 *
 * Cómo correrlo a mano:
 * 1. Levantar un emulador Android (API 26+, el `minSdk` del proyecto) o conectar un dispositivo físico.
 * 2. `./gradlew :shared:connectedDebugAndroidTest` (o el target de `connectedAndroidTest` equivalente que
 *    genere el plugin de Android para el módulo `shared`).
 *
 * Para probar a mano el caso de invalidación de clave (`KeyPermanentlyInvalidatedException`, la parte que
 * *no* cubre este archivo porque requiere cambiar el bloqueo de pantalla del dispositivo entre `guardar` y
 * `leer`, algo que un test instrumentado no puede automatizar sin UI Automator):
 * 1. Correr `guardar()` con una sesión de prueba.
 * 2. Desde Ajustes del dispositivo, quitar o cambiar el PIN/patrón de bloqueo de pantalla (esto invalida
 *    cualquier clave de Keystore generada con `setUserAuthenticationRequired` ligado al bloqueo -- aunque
 *    acá se generó con `setUserAuthenticationRequired(false)`, el caso general de invalidación también
 *    puede darse por un factory reset parcial o restauración; el objetivo de la prueba manual es confirmar
 *    que `leer()` nunca crashea, siempre da `null` cuando la clave ya no es utilizable).
 * 3. Llamar a `leer()` y confirmar que devuelve `null` en vez de lanzar.
 */
class SecureTokenStorageAndroidTest {

    private val storage = SecureTokenStorage(InstrumentationRegistry.getInstrumentation().targetContext)

    private val sesion = SesionPersistida(
        token = "token-de-prueba",
        rol = Rol.ACOPIADOR,
        nombre = "Ana",
        usuarioId = "u-123",
        expiraEnEpochMillis = 1_999_999_999_000L,
    )

    @After
    fun limpiar() = runTest {
        storage.borrar()
    }

    @Test
    fun guardarYLeerDevuelveExactamenteLoGuardado() = runTest {
        storage.borrar()

        storage.guardar(sesion)
        val leido = storage.leer()

        assertEquals(sesion, leido)
    }

    @Test
    fun leerSinHaberGuardadoNuncaDevuelveNull() = runTest {
        storage.borrar()

        assertNull(storage.leer())
    }

    @Test
    fun leerDespuesDeBorrarDevuelveNull() = runTest {
        storage.guardar(sesion)

        storage.borrar()

        assertNull(storage.leer())
    }

    @Test
    fun guardarDosVecesActualizaElValor() = runTest {
        storage.borrar()
        storage.guardar(sesion)

        val sesionActualizada = sesion.copy(token = "token-actualizado")
        storage.guardar(sesionActualizada)

        assertEquals(sesionActualizada, storage.leer())
    }
}
