package com.ecolacteos.acopio.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/**
 * Target `jvm()` -- existe solo para que `:shared:jvmTest` compile sin emulador/simulador
 * (`MOBILE_ARCHITECTURE.md §14`, "Testing"). Nunca se empaqueta en producción: Android e iOS tienen sus
 * propios `actual` sobre SQLite real (mismo criterio que `SecureTokenStorage.jvm.kt`, Fase 3). En memoria,
 * se recrea vacía en cada proceso -- por eso arma el esquema acá mismo, algo que Android/iOS no necesitan
 * porque sus drivers reales lo hacen solos al abrir el archivo.
 */
actual class AcopioDriverFactory {
    actual fun crearDriver(): SqlDriver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AcopioDatabase.Schema.create(it) }
}
