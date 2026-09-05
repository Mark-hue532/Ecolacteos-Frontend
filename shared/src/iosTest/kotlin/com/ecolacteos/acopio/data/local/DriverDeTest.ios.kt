package com.ecolacteos.acopio.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

// Primer uso real de NativeSqliteDriver en el proyecto (PROMPT_FASE_04.md §7) -- ":memory:" es el nombre
// estándar de SQLite para una base efímera, el constructor ya deja el esquema creado.
actual fun crearDriverDeTest(): SqlDriver = NativeSqliteDriver(AcopioDatabase.Schema, ":memory:")
