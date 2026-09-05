package com.ecolacteos.acopio.data.local.adapter

import app.cash.sqldelight.ColumnAdapter
import com.ecolacteos.acopio.domain.model.SyncStatus

/**
 * `SyncStatus ↔ TEXT`, **sin** fallback (`PROMPT_FASE_04.md §3`, a propósito asimétrico con
 * [EnumConReservaColumnAdapter]): `SyncStatus` no tiene contraparte remota, solo esta app la escribe y la
 * lee -- si `valueOf()` lanza acá es un bug propio (ej. un valor que esta misma versión nunca escribió),
 * no un dato ajeno inesperado que haya que tolerar.
 */
object SyncStatusColumnAdapter : ColumnAdapter<SyncStatus, String> {
    override fun decode(databaseValue: String): SyncStatus = SyncStatus.valueOf(databaseValue)
    override fun encode(value: SyncStatus): String = value.name
}
