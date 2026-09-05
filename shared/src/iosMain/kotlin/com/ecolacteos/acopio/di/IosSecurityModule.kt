package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.security.AlmacenamientoSeguroDeSesion
import com.ecolacteos.acopio.security.SecureTokenStorage
import org.koin.dsl.module

/**
 * Binding de [SecureTokenStorage] específico de iOS -- mismo motivo que en `androidApp/MainActivity.kt`:
 * el `expect class SecureTokenStorage` no declara constructor común, así que cada plataforma lo registra
 * en su propio módulo (`security/SecureTokenStorage.kt`). En iOS no hace falta nada externo (a diferencia
 * de Android, que necesita `Context`), así que esta función no recibe parámetros.
 *
 * La expone `shared` (en vez de que `iosApp/iOSApp.swift` arme el `module { }` a mano) porque la DSL de
 * Koin (`module { single { ... } }`) es Kotlin puro -- no hay forma razonable de escribirla desde Swift.
 * `iOSApp.swift` solo tiene que llamar a esta función y pasarla a `initKoin`.
 */
fun moduloSeguridadIos() = module {
    single<AlmacenamientoSeguroDeSesion> { SecureTokenStorage() }
}
