package com.ecolacteos.acopio.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

// `androidUnitTest` corre sobre el JVM del host, sin Context real -- mismo driver JDBC en memoria que
// jvmTest (PROMPT_FASE_04.md §7: "cubre también el test unitario de Android"), duplicado a propósito
// porque jvm() y androidTarget() son targets KMP distintos aunque ambos corran sobre el mismo JVM.
actual fun crearDriverDeTest(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AcopioDatabase.Schema.create(it) }
