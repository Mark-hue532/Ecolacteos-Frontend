package com.ecolacteos.acopio.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val PROVEEDOR_KEYSTORE = "AndroidKeyStore"
private const val ALIAS_CLAVE = "com.ecolacteos.acopio.sesion" // fijo: se genera en el primer uso si no existe
private const val TRANSFORMACION = "AES/GCM/NoPadding"
private const val TAMANO_TAG_GCM_BITS = 128
private const val PREFS_NOMBRE = "com.ecolacteos.acopio.sesion_segura"
private const val PREF_CIPHERTEXT = "ciphertext"
private const val PREF_IV = "iv"

/**
 * AES/256/GCM con la clave en `AndroidKeyStore`, ciphertext + IV en `SharedPreferences` normales
 * (`PROMPT_FASE_03.md §2`). No `EncryptedSharedPreferences`/`androidx.security-crypto`: está deprecado por
 * Google, y para cinco strings una capa fina propia es más auditable que sumar esa dependencia (decisión
 * documentada en el checkpoint de esta fase, discutida contra `MOBILE_ARCHITECTURE.md §14`).
 *
 * `context` recibido por constructor -- lo arma `androidApp/MainActivity.kt` al declarar el módulo de Koin
 * de esta plataforma, `commonMain` nunca instancia esto directamente (ver `security/SecureTokenStorage.kt`).
 */
actual class SecureTokenStorage(context: Context) : AlmacenamientoSeguroDeSesion {

    // applicationContext, no la Activity -- SharedPreferences no necesita mantener viva una Activity.
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NOMBRE, Context.MODE_PRIVATE)

    actual override suspend fun guardar(sesion: SesionPersistida) {
        val clave = obtenerOCrearClave()
        val cipher = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, clave) }
        // GCM: el Keystore genera un IV nuevo en cada init(ENCRYPT_MODE) -- hay que guardarlo para poder
        // descifrar despues, es el detalle que esta implementacion se olvida mas seguido (PROMPT_FASE_03.md §2).
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(sesion.aJson().encodeToByteArray())
        prefs.edit()
            .putString(PREF_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    actual override suspend fun leer(): SesionPersistida? {
        val ciphertextB64 = prefs.getString(PREF_CIPHERTEXT, null) ?: return null
        val ivB64 = prefs.getString(PREF_IV, null) ?: return null

        return try {
            val clave = obtenerClaveExistente() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMACION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    clave,
                    GCMParameterSpec(TAMANO_TAG_GCM_BITS, Base64.decode(ivB64, Base64.NO_WRAP)),
                )
            }
            val textoPlano = cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP))
            textoPlano.decodeToString().aSesionPersistida()
        } catch (e: GeneralSecurityException) {
            // La clave puede invalidarse (cambio de bloqueo de pantalla, restauracion del dispositivo,
            // etc.): descifrar lanza KeyPermanentlyInvalidatedException u otra GeneralSecurityException.
            // No debe crashear -- se trata como "no hay sesion" y se limpia lo que quedo (PROMPT_FASE_03.md §2).
            borrarSincrono()
            null
        } catch (e: IllegalArgumentException) {
            // Base64 corrupto (no debería pasar nunca porque somos los únicos que escribimos acá, pero un
            // valor a medio escribir por un crash entre los dos `putString` no debe crashear tampoco).
            borrarSincrono()
            null
        }
    }

    actual override suspend fun borrar() {
        borrarSincrono()
    }

    private fun borrarSincrono() {
        prefs.edit().remove(PREF_CIPHERTEXT).remove(PREF_IV).apply()
    }

    private fun obtenerClaveExistente(): SecretKey? {
        val keyStore = KeyStore.getInstance(PROVEEDOR_KEYSTORE).apply { load(null) }
        return keyStore.getKey(ALIAS_CLAVE, null) as? SecretKey
    }

    private fun obtenerOCrearClave(): SecretKey {
        obtenerClaveExistente()?.let { return it }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(
            ALIAS_CLAVE,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Si se exigiera autenticacion del usuario, el sync en background no podria leer el token con
            // la pantalla bloqueada -- rompe el caso de uso central de la app (PROMPT_FASE_03.md §2).
            .setUserAuthenticationRequired(false)

        // StrongBox si el hardware lo soporta, con fallback silencioso si no -- los equipos de esta app
        // son de gama baja (CLAUDE.md §1) y no todos lo tienen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generador.init(specBuilder.setIsStrongBoxBacked(true).build())
                return generador.generateKey()
            } catch (e: StrongBoxUnavailableException) {
                // sigue abajo sin StrongBox
            }
        }
        generador.init(specBuilder.setIsStrongBoxBacked(false).build())
        return generador.generateKey()
    }
}
