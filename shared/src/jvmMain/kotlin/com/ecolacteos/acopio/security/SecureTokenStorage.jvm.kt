package com.ecolacteos.acopio.security

// Target `jvm()` -- existe solo para que `:shared:jvmTest`/`checkModules()` compilen y corran sin
// emulador/simulador (MOBILE_ARCHITECTURE.md §14, "Testing"). Nunca se empaqueta en producción: Android e
// iOS tienen sus propios `actual` sobre Keystore/Keychain. En memoria, sin ningún cifrado -- si algún día
// esto corriera fuera de un test, guardar el token en claro sería tan malo como el problema que
// SecureTokenStorage existe para evitar.
actual class SecureTokenStorage : AlmacenamientoSeguroDeSesion {
    private var sesion: SesionPersistida? = null

    actual override suspend fun guardar(sesion: SesionPersistida) {
        this.sesion = sesion
    }

    actual override suspend fun leer(): SesionPersistida? = sesion

    actual override suspend fun borrar() {
        sesion = null
    }
}
