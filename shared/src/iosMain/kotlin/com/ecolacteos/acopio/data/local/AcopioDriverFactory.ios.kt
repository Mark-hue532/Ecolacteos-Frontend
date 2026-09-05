package com.ecolacteos.acopio.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/** iOS no necesita nada para construirse -- Keychain/Context no existen acá (mismo contraste que Fase 3). */
actual class AcopioDriverFactory {
    actual fun crearDriver(): SqlDriver = NativeSqliteDriver(AcopioDatabase.Schema, NOMBRE_BASE_DE_DATOS)
}
