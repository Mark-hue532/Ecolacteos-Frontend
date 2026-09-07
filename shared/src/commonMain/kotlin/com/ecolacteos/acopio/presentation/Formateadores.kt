package com.ecolacteos.acopio.presentation

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Formateadores de UI (`MOBILE_SCREENS.md §10.1`, §10.2`). Viven en `presentation/` -- no en `core/` -- a
 * propósito: son reglas de **presentación** (cómo se ve un dato), no del contrato de datos. Se calculan en
 * el `ViewModel` (`§10.1` regla 1), nunca dentro de un `@Composable`.
 *
 * `commonMain` nunca usa `java.text.DecimalFormat`/`SimpleDateFormat` (`CLAUDE.md §8`, trampa #15) -- todo
 * acá es manipulación de texto/enteros sobre tipos `kotlinx.datetime`/`bignum`.
 */

/** `dd/MM/yyyy` (`§10.2`). Ej. `04/09/2026`. */
@Suppress("DEPRECATION")
fun LocalDate.formateada(): String = "${dayOfMonth.dosDigitos()}/${monthNumber.dosDigitos()}/$year"

/** `dd/MM/yyyy HH:mm` (`§10.2`), tal cual llega -- nunca convertida de zona (`§10.3`). Ej. `04/09/2026 16:20`. */
fun LocalDateTime.formateada(): String = "${date.formateada()} ${hour.dosDigitos()}:${minute.dosDigitos()}"

/**
 * `HH:mm` (`§10.2`, `A-01`). Ej. `14:30`. Agregada en la Fase 8A -- `horaEstimada` es el primer campo
 * `LocalTime` que llega a la capa de presentación; `§10.1` regla 3 sigue aplicando: un `horaEstimada` nulo
 * nunca se formatea acá, se omite en el `ViewModel` antes de llegar a esta función (nunca `"--:--"`).
 */
fun LocalTime.formateada(): String = "${hour.dosDigitos()}:${minute.dosDigitos()}"

private fun Int.dosDigitos(): String = toString().padStart(2, '0')

/**
 * Escala fija por campo (`§10.1` tabla). `null` nunca se formatea como `0` (regla 3) -- se devuelve `null`
 * y quien arma el `UiState` decide si omitir el campo o escribir "No disponible".
 */
fun Decimal?.formateadoConEscala(escala: Int): String? = this?.aTextoConEscala(escala)

/** `"No disponible"` para un decimal ausente que sí se quiere mostrar como fila, en vez de omitirla. */
const val NO_DISPONIBLE: String = "No disponible"
