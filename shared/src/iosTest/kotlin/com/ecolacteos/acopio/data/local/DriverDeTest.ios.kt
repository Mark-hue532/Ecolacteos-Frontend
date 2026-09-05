package com.ecolacteos.acopio.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

// En SQLDelight Native, `name = null` crea una DB realmente en memoria y aislada por driver de test.
// Usar la cadena ":memory:" crea/rehúsa un archivo con ese nombre, contaminando casos entre sí.
actual fun crearDriverDeTest(): SqlDriver = NativeSqliteDriver(AcopioDatabase.Schema, name = null)
