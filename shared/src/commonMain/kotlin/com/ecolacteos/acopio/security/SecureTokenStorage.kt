package com.ecolacteos.acopio.security

/**
 * Almacenamiento seguro nativo de la sesión (`PROMPT_FASE_03.md §1`, `MOBILE_ARCHITECTURE.md §4`):
 * Android Keystore (AES/256/GCM, ver `SecureTokenStorage.android.kt`) e iOS Keychain (ver
 * `SecureTokenStorage.ios.kt`). Superficie mínima a propósito -- cuantas menos funciones, más fácil de
 * auditar el único lugar de `shared/` que toca la credencial del usuario.
 *
 * Sin constructor declarado acá a propósito: cada plataforma necesita algo distinto para construirse
 * (Android, un `Context`; iOS, nada) y ninguna lo puede recibir desde código común de todos modos --
 * `commonMain` nunca instancia `SecureTokenStorage()` directamente, la arma el módulo de Koin de cada
 * plataforma (`androidApp`/`MainActivity.kt`, futuro `iosApp`). El target `jvm()` (`shared/build.gradle.kts`)
 * también necesita un `actual`, aunque nunca se empaqueta en producción: existe solo para que
 * `:shared:jvmTest` compile sin emulador/simulador (`MOBILE_ARCHITECTURE.md §14`, "Testing"); su
 * implementación es en memoria, ver `SecureTokenStorage.jvm.kt`.
 */
expect class SecureTokenStorage : AlmacenamientoSeguroDeSesion {
    override suspend fun guardar(sesion: SesionPersistida)
    override suspend fun leer(): SesionPersistida?
    override suspend fun borrar()
}
