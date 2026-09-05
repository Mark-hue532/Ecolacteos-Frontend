package com.ecolacteos.acopio.data.local.adapter

import app.cash.sqldelight.ColumnAdapter

/**
 * `Int ↔ INTEGER`. SQLite `INTEGER` es 8 bytes y el tipo por defecto que SQLDelight infiere es `Long`;
 * columnas que son contadores pequeños (`sync_attempts`, `unidades_obtenidas`, `cantidad`, `orden`) se
 * declaran `INTEGER AS kotlin.Int` en el `.sq` para no arrastrar `Long` a los modelos de dominio.
 *
 * **Trampa confirmada compilando, no asumida** (`PROMPT_FASE_04.md §3`, "Boolean ↔ INTEGER"): `INTEGER AS
 * Boolean` (sin calificar) generaba un `import Boolean` roto -- el `.sq` lo trataba como un tipo propio sin
 * resolver, exigiendo un `ColumnAdapter<Boolean, Long>` explícito. Con el nombre completo (`INTEGER AS
 * kotlin.Boolean`) sí resuelve nativo: SQLDelight `2.3.2` no necesita ningún adapter para `Boolean` (por
 * eso no existe un `BooleanColumnAdapter` en este archivo). `Int`, en cambio, **sí** sigue pidiendo este
 * adapter explícito aun calificado -- no hay soporte nativo equivalente para angostar `Long` a `Int`.
 */
object IntColumnAdapter : ColumnAdapter<Int, Long> {
    override fun decode(databaseValue: Long): Int = databaseValue.toInt()
    override fun encode(value: Int): Long = value.toLong()
}
