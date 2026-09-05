package com.ecolacteos.acopio.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val SERVICE = "com.ecolacteos.acopio.sesion"
private const val ACCOUNT = "sesion"

/**
 * Keychain (`kSecClassGenericPassword`) vía la API C de `Security.framework` (`PROMPT_FASE_03.md §3`).
 *
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: `AfterFirstUnlock` (no `WhenUnlocked`) porque el sync
 * en background necesita leer el token con el dispositivo bloqueado; `ThisDeviceOnly` para que el token no
 * viaje al Keychain de iCloud ni se restaure en otro dispositivo.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SecureTokenStorage : AlmacenamientoSeguroDeSesion {

    actual override suspend fun guardar(sesion: SesionPersistida) {
        val data = nsDataDesdeTexto(sesion.aJson())

        // SecItemUpdate si ya existe -- no acumular entradas duplicadas (PROMPT_FASE_03.md §3).
        val actualizacion = NSMutableDictionary().apply { setObject(data, forKey = kSecValueData.nsKey()) }
        val statusUpdate = SecItemUpdate(queryBase().asCFDictionary(), actualizacion.asCFDictionary())

        if (statusUpdate == errSecItemNotFound) {
            val nuevoItem = queryBaseMutable().apply {
                setObject(data, forKey = kSecValueData.nsKey())
                setObject(
                    kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    forKey = kSecAttrAccessible.nsKey(),
                )
            }
            SecItemAdd(nuevoItem.asCFDictionary(), null)
        }
    }

    actual override suspend fun leer(): SesionPersistida? {
        val query = queryBaseMutable().apply {
            setObject(true, forKey = kSecReturnData.nsKey())
            setObject(kSecMatchLimitOne, forKey = kSecMatchLimit.nsKey())
        }

        return memScoped {
            val resultado = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.asCFDictionary(), resultado.ptr)
            // errSecItemNotFound se maneja como "no hay sesion", no como error (PROMPT_FASE_03.md §3).
            if (status != errSecSuccess) return@memScoped null

            val valor = resultado.value ?: return@memScoped null
            val data = interpretObjCPointer<NSData>(valor.rawValue)
            textoDesdeNsData(data)?.aSesionPersistida()
        }
    }

    actual override suspend fun borrar() {
        SecItemDelete(queryBase().asCFDictionary())
    }

    private fun queryBase(): NSMutableDictionary = queryBaseMutable()

    private fun queryBaseMutable(): NSMutableDictionary = NSMutableDictionary().apply {
        setObject(kSecClassGenericPassword, forKey = kSecClass.nsKey())
        setObject(SERVICE, forKey = kSecAttrService.nsKey())
        setObject(ACCOUNT, forKey = kSecAttrAccount.nsKey())
    }
}

/**
 * Las claves de `NSDictionary` (`forKey:`) exigen `NSCopying`, no cualquier `Any?` -- a diferencia del
 * valor (`anObject:`), que sí acepta un `CFStringRef` crudo sin problema. Las constantes `kSecXxx` de
 * `Security.framework` llegan a Kotlin como `CFStringRef` (un `CPointer` de interop), no como `NSString`,
 * aunque a nivel binario **son el mismo objeto** (toll-free bridging, RFC de Apple): no hay conversión real
 * que hacer, solo reinterpretar el mismo puntero con el tipo Kotlin que `setObject` pide.
 */
@OptIn(ExperimentalForeignApi::class)
private fun CFStringRef?.nsKey(): NSString = interpretObjCPointer<NSString>(this!!.rawValue)

/**
 * La contraparte de [nsKey] en la otra dirección: `SecItemAdd`/`Update`/`CopyMatching`/`Delete` piden
 * `CFDictionaryRef`, no `NSDictionary` -- un `as CFDictionaryRef` directo falla en tiempo de compilación
 * ("this cast can never succeed", son jerarquías de clases Kotlin distintas), aunque a nivel binario es el
 * mismo `NSMutableDictionary` de siempre. `objcPtr()` da el puntero crudo del objeto Objective-C
 * subyacente; `reinterpret()` solo cambia el tipo Kotlin con el que se lo referencia, sin tocar el objeto.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSDictionary.asCFDictionary(): CFDictionaryRef = interpretCPointer(this.objcPtr())!!

@OptIn(kotlinx.cinterop.BetaInteropApi::class)
private fun nsDataDesdeTexto(texto: String): NSData =
    NSString.create(string = texto).dataUsingEncoding(NSUTF8StringEncoding)!!

@OptIn(kotlinx.cinterop.BetaInteropApi::class)
private fun textoDesdeNsData(data: NSData): String? =
    NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
