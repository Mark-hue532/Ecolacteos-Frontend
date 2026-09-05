package com.ecolacteos.acopio.core

import com.ionspin.kotlin.bignum.decimal.RoundingMode

/**
 * Tipo decimal del proyecto. Todo campo que en el backend es `java.math.BigDecimal` (litros, precios,
 * totales, porcentajes, GPS, medidas de laboratorio) viaja como este tipo -- nunca como `Double`, ni
 * siquiera intermedio (CLAUDE.md §3.1, ver DATA-002).
 *
 * `bignum` es la dependencia CRITICAL de esta fase: está publicada contra una versión de Kotlin anterior a
 * la de este proyecto y su compatibilidad de klibs en Kotlin/Native (iOS) se verifica en el CI de la Fase 1
 * (`.github/workflows/verificacion-ios.yml`), no localmente.
 */
typealias Decimal = com.ionspin.kotlin.bignum.decimal.BigDecimal

/**
 * Parsea un número decimal crudo (tal como llega en el JSON o se tipea en un formulario) sin pasar nunca
 * por `Double`. Lanza `NumberFormatException` si [texto] no es un número válido -- responsabilidad del
 * llamador (formulario o `KSerializer`, según capa) decidir qué hacer con el error.
 */
fun decimalDesdeTexto(texto: String): Decimal = Decimal.parseString(texto)

/**
 * Formatea con escala fija exacta, vía manipulación de texto -- nunca redondeando a través de `Double`.
 * La escala se recibe como parámetro (tabla de escalas por campo: `MOBILE_SCREENS.md §10.1`); esta fase
 * solo provee el helper genérico.
 *
 * `"12.5"` con `escala = 2` da `"12.50"`; `"12.567"` con `escala = 2` da `"12.57"` (redondeo half-up).
 * Un roundtrip `String -> Decimal -> String` con la misma escala es siempre idéntico.
 */
fun Decimal.aTextoConEscala(escala: Int): String {
    require(escala >= 0) { "La escala no puede ser negativa: $escala" }
    val redondeado = this.roundToDigitPositionAfterDecimalPoint(
        digitPosition = escala.toLong(),
        roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO,
    )
    return normalizarEscalaTexto(redondeado.toStringExpanded(), escala)
}

/**
 * Ajusta la cantidad de decimales de una representación textual ya redondeada, rellenando con ceros o
 * truncando -- puramente sobre `String`, para no depender de si `bignum` conserva o no los ceros finales
 * en su representación interna.
 */
private fun normalizarEscalaTexto(texto: String, escala: Int): String {
    val esNegativo = texto.startsWith("-")
    val sinSigno = if (esNegativo) texto.substring(1) else texto
    val puntoIndice = sinSigno.indexOf('.')
    val parteEntera = if (puntoIndice >= 0) sinSigno.substring(0, puntoIndice) else sinSigno
    val parteDecimal = if (puntoIndice >= 0) sinSigno.substring(puntoIndice + 1) else ""

    val decimalAjustado = when {
        escala == 0 -> ""
        parteDecimal.length >= escala -> parteDecimal.substring(0, escala)
        else -> parteDecimal.padEnd(escala, '0')
    }

    val resultado = if (escala == 0) parteEntera else "$parteEntera.$decimalAjustado"
    return if (esNegativo) "-$resultado" else resultado
}
