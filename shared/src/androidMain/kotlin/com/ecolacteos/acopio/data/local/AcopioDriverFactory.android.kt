package com.ecolacteos.acopio.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * `context` recibido por constructor -- lo arma el módulo de Koin de `androidApp` cuando exista un
 * consumidor real (mismo criterio que `SecureTokenStorage.android.kt`, Fase 3). `applicationContext`, no
 * la Activity: la base de datos vive más que cualquier Activity puntual.
 */
actual class AcopioDriverFactory(private val context: Context) {
    actual fun crearDriver(): SqlDriver =
        AndroidSqliteDriver(AcopioDatabase.Schema, context.applicationContext, NOMBRE_BASE_DE_DATOS)
}
