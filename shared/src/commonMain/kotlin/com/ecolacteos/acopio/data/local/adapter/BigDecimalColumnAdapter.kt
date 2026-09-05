package com.ecolacteos.acopio.data.local.adapter

import app.cash.sqldelight.ColumnAdapter
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala

/**
 * `BigDecimal ↔ TEXT` para SQLite (`PROMPT_FASE_04.md §3`, misma regla de `DATA-002` que ya aplicó el
 * `KSerializer` de Fase 2, ahora del otro lado del Repository).
 *
 * ⚠️ **Hallazgo de esta fase, confirmado con un test que falló antes de este fix** (mismo hallazgo que ya
 * documentó `BigDecimalSerializer.kt` de Fase 2, ahora reproducido en SQLite): `bignum`
 * (`Decimal.parseString`) **no** conserva los ceros finales del literal -- `parseString("12.50")
 * .toStringExpanded() == "12.5"`, no `"12.50"`. Un `ColumnAdapter` genérico sobre `toStringExpanded()` sin
 * escala fija reintroduce justo la inconsistencia de formato que `DATA-002` pide evitar (aunque el *valor*
 * numérico no se pierda, "12.5" y "12.50" no son el mismo texto, y esta capa debe coincidir con lo que
 * Fase 2 ya serializa/deserializa para el mismo campo). Por eso este `ColumnAdapter` es **abstracto y
 * parametrizado por escala**, igual que `BigDecimalConEscalaSerializer`, reusando el mismo
 * `Decimal.aTextoConEscala(escala)` de `core/Decimal.kt` (redondeo half-up + relleno de ceros vía texto,
 * nunca vía `Double`) -- nunca `toStringExpanded()` a secas.
 */
abstract class BigDecimalConEscalaColumnAdapter(private val escala: Int) : ColumnAdapter<Decimal, String> {
    override fun decode(databaseValue: String): Decimal = Decimal.parseString(databaseValue)
    override fun encode(value: Decimal): String = value.aTextoConEscala(escala)
}

/** Escala 2 -- la enorme mayoría de las columnas decimales de `data/local/` (`MOBILE_DATA_MAPPING.md §5`). */
object BigDecimalEscala2ColumnAdapter : BigDecimalConEscalaColumnAdapter(escala = 2)

/** Escala 6 -- únicamente `gps_lat`/`gps_lng` de `registro_acopio_local` (`precision=9,scale=6`). */
object BigDecimalEscala6ColumnAdapter : BigDecimalConEscalaColumnAdapter(escala = 6)
