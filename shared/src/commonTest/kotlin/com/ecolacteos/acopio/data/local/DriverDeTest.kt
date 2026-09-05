package com.ecolacteos.acopio.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * Driver de SQLite en memoria para tests (`PROMPT_FASE_04.md §7`). Un `actual` por target de test:
 * `jvmTest`/`androidUnitTest` usan el driver JDBC en memoria (`JdbcSqliteDriver.IN_MEMORY`) -- un
 * `androidUnitTest` corre sobre el JVM del host sin `Context` real, así que no puede usar
 * `AndroidSqliteDriver` (eso necesita un dispositivo/emulador, fuera de alcance de esta fase). `iosTest`
 * usa `NativeSqliteDriver` real sobre SQLite nativo -- el primer uso real de un driver de SQLDelight en
 * iOS de todo el proyecto. Cada `actual` deja el esquema ya creado (`AcopioDatabase.Schema.create(...)`
 * o, en el caso nativo, el propio constructor de `NativeSqliteDriver` lo hace).
 */
expect fun crearDriverDeTest(): SqlDriver
