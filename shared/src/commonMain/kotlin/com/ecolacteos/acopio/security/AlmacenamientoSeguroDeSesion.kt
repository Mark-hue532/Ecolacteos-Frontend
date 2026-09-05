package com.ecolacteos.acopio.security

/**
 * Contrato mínimo de [SecureTokenStorage], separado en interfaz para que `GestorSesion` (y sus tests en
 * `commonTest`) dependan de esto y no del `expect class` concreto -- un `expect class` no se puede fakear
 * desde código común (cada plataforma tendría que declarar su propio fake `actual`, lo que derrota el
 * propósito). La implementación real sobre Keystore/Keychain (`SecureTokenStorage`) es la única pensada
 * para producción; en tests se usa un fake liviano que implementa esta misma interfaz.
 */
interface AlmacenamientoSeguroDeSesion {
    suspend fun guardar(sesion: SesionPersistida)
    suspend fun leer(): SesionPersistida?
    suspend fun borrar()
}
