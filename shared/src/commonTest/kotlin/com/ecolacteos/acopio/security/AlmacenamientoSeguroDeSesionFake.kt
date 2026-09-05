package com.ecolacteos.acopio.security

/**
 * Fake en memoria de [AlmacenamientoSeguroDeSesion] para tests de dominio (`PROMPT_FASE_03.md §8`): corre
 * en JVM y en iOS sin tocar Keystore/Keychain real -- eso lo cubren `SecureTokenStorageAndroidTest`
 * (instrumentado, sin ejecutar en CI) y `SecureTokenStorageIosTest` (`iosSimulatorArm64Test`, sí en CI).
 */
class AlmacenamientoSeguroDeSesionFake : AlmacenamientoSeguroDeSesion {
    private var sesion: SesionPersistida? = null

    /** El JSON que se habría persistido de verdad -- para el test de "la contraseña nunca se persiste". */
    var ultimoJsonGuardado: String? = null
        private set

    override suspend fun guardar(sesion: SesionPersistida) {
        this.sesion = sesion
        ultimoJsonGuardado = sesion.aJson()
    }

    override suspend fun leer(): SesionPersistida? = sesion

    override suspend fun borrar() {
        sesion = null
    }
}
