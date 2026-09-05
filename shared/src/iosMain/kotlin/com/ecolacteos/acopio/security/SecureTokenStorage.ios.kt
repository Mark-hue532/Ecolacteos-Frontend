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
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.numberWithBool
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
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

    /**
     * Último `OSStatus` que devolvió `Security.framework`, **solo para diagnóstico** (lo lee
     * `SecureTokenStorageIosTest`). No es parte del contrato de [AlmacenamientoSeguroDeSesion].
     *
     * Existe porque la primera versión de esta clase ignoraba el status de `SecItemAdd`/`SecItemUpdate`:
     * un fallo de escritura quedaba invisible y la app se comportaba como si la sesión se hubiera guardado
     * hasta el siguiente arranque. Que el test pueda afirmar `errSecSuccess` convierte ese fallo silencioso
     * en un fallo ruidoso. Ver el checkpoint de la Fase 3: queda pendiente decidir si `guardar()` debería
     * además poder devolverle el fallo a quien la llama (hoy su firma es `Unit`, fijada por el prompt).
     */
    internal var ultimoStatus: Int = errSecSuccess
        private set

    actual override suspend fun guardar(sesion: SesionPersistida) {
        val data = nsDataDesdeTexto(sesion.aJson())

        val nuevoItem = queryBaseMutable().apply {
            setObject(data, forKey = kSecValueData.comoNSString())
            setObject(
                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly.comoNSString(),
                forKey = kSecAttrAccessible.comoNSString(),
            )
        }

        // Add primero y update solo si ya existía: `errSecDuplicateItem` es una respuesta bien definida de
        // SecItemAdd, mientras que hacerlo al revés obliga a acertarle al código exacto con el que
        // SecItemUpdate reporta "no existe" (PROMPT_FASE_03.md §3 -- lo que importa es no acumular
        // entradas duplicadas, no el orden).
        var status = SecItemAdd(nuevoItem.asCFDictionary(), null)
        if (status == errSecDuplicateItem) {
            val actualizacion = NSMutableDictionary().apply {
                setObject(data, forKey = kSecValueData.comoNSString())
            }
            status = SecItemUpdate(queryBase().asCFDictionary(), actualizacion.asCFDictionary())
        }
        ultimoStatus = status
    }

    actual override suspend fun leer(): SesionPersistida? {
        val query = queryBaseMutable().apply {
            setObject(NSNumber.numberWithBool(true), forKey = kSecReturnData.comoNSString())
            setObject(kSecMatchLimitOne.comoNSString(), forKey = kSecMatchLimit.comoNSString())
        }

        return memScoped {
            val resultado = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.asCFDictionary(), resultado.ptr)
            ultimoStatus = status
            // errSecItemNotFound se maneja como "no hay sesion", no como error (PROMPT_FASE_03.md §3).
            if (status != errSecSuccess) return@memScoped null

            val valor = resultado.value ?: return@memScoped null
            val data = interpretObjCPointer<NSData>(valor.rawValue)
            textoDesdeNsData(data)?.aSesionPersistida()
        }
    }

    actual override suspend fun borrar() {
        val status = SecItemDelete(queryBase().asCFDictionary())
        // errSecItemNotFound al borrar algo que no existe no es un fallo: el resultado buscado ya se cumple.
        ultimoStatus = if (status == errSecItemNotFound) errSecSuccess else status
    }

    private fun queryBase(): NSMutableDictionary = queryBaseMutable()

    private fun queryBaseMutable(): NSMutableDictionary = NSMutableDictionary().apply {
        setObject(kSecClassGenericPassword.comoNSString(), forKey = kSecClass.comoNSString())
        setObject(SERVICE.comoNSString(), forKey = kSecAttrService.comoNSString())
        setObject(ACCOUNT.comoNSString(), forKey = kSecAttrAccount.comoNSString())
    }
}

/**
 * Las constantes `kSecXxx` de `Security.framework` llegan a Kotlin como `CFStringRef` (un `CPointer` de
 * interop), no como `NSString`, aunque a nivel binario **son el mismo objeto** (toll-free bridging): no hay
 * conversión real que hacer, solo reinterpretar el mismo puntero con el tipo Kotlin que `NSDictionary`
 * espera.
 *
 * ⚠️ Hace falta tanto en las **claves** como en los **valores**, y esa asimetría es una trampa: `forKey:`
 * exige `NSCopying` y el compilador obliga a convertir, pero `anObject:` acepta `Any?` y deja pasar el
 * `CPointer` crudo sin una sola advertencia -- que en runtime se boxea como objeto Kotlin en vez de viajar
 * como la CFString que Keychain espera. El resultado es un `SecItemAdd` que falla en silencio (se vio en el
 * CI de la Fase 3: los tests que solo leían pasaban, los que escribían fallaban).
 */
@OptIn(ExperimentalForeignApi::class)
private fun CFStringRef?.comoNSString(): NSString = interpretObjCPointer<NSString>(this!!.rawValue)

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
private fun String.comoNSString(): NSString = NSString.create(string = this)

@OptIn(kotlinx.cinterop.BetaInteropApi::class)
private fun textoDesdeNsData(data: NSData): String? =
    NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
