package com.ecolacteos.acopio.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import platform.Foundation.NSUUID

/**
 * Crea un [SqlDriver] aislado para cada test en iOS.
 * 
 * Usar un nombre único basado en [NSUUID] evita que múltiples llamadas a [crearDriverDeTest]
 * o ejecuciones en paralelo compartan la misma base de datos en memoria, garantizando
 * un entorno limpio por cada prueba.
 */
actual fun crearDriverDeTest(): SqlDriver {
    val name = "test_${NSUUID.UUID().UUIDString}.db"
    return NativeSqliteDriver(AcopioDatabase.Schema, name)
}
