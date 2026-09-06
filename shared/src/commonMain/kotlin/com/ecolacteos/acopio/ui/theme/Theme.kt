package com.ecolacteos.acopio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tema mínimo y funcional (`MOBILE_SCREENS.md §14`: "la capa visual la entrega diseño y todavía no
 * existe" -- esto no es el sistema de diseño final, es lo sobrio e imprescindible para que las 7 pantallas
 * de esta fase cumplan `§15`: alto contraste, toques grandes, legible bajo sol).
 *
 * Paleta verdosa suave (lácteo/agrícola, `§14` punto 1), nunca fosforescente. `exito`/`atencion`/`error`
 * son los tres estados de los badges de sync (`§10.5`) -- se exponen acá porque `ColorScheme` de Material 3
 * no tiene un slot propio para "pendiente de sincronizar".
 */
object AcopioColores {
    val primario = Color(0xFF3E6B4F)
    val primarioClaro = Color(0xFFD9EAD9)
    val superficie = Color(0xFFFAFAF7)
    val superficieOscura = Color(0xFF1B2420)
    val exito = Color(0xFF3E7A4C)
    val atencion = Color(0xFFB07A1E)
    val error = Color(0xFFB3261E)
    val textoAlto = Color(0xFF1A1C19)
    val textoAltoOscuro = Color(0xFFE2E3DE)
}

private val esquemaClaro = lightColorScheme(
    primary = AcopioColores.primario,
    primaryContainer = AcopioColores.primarioClaro,
    background = AcopioColores.superficie,
    surface = AcopioColores.superficie,
    error = AcopioColores.error,
    onBackground = AcopioColores.textoAlto,
    onSurface = AcopioColores.textoAlto,
)

private val esquemaOscuro = darkColorScheme(
    primary = AcopioColores.primarioClaro,
    primaryContainer = AcopioColores.primario,
    background = AcopioColores.superficieOscura,
    surface = AcopioColores.superficieOscura,
    error = AcopioColores.error,
    onBackground = AcopioColores.textoAltoOscuro,
    onSurface = AcopioColores.textoAltoOscuro,
)

/** Escala tipográfica mínima -- tamaños grandes a propósito (`§15`, uso con guantes y sol directo). */
private val tipografia = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
)

/** Espaciado del sistema (`§14`) -- un único lugar, para no repetir `dp` sueltos por cada pantalla. */
object Espaciado {
    val xs = 4
    val s = 8
    val m = 16
    val l = 24
    val xl = 32
}

/** Objetivo táctil mínimo de `§15`: "mínimo 48 dp, preferentemente más en las acciones primarias". */
object Toques {
    val minimo = 48
    val principal = 56
}

@Composable
fun AcopioTheme(oscuro: Boolean = isSystemInDarkTheme(), contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (oscuro) esquemaOscuro else esquemaClaro,
        typography = tipografia,
        content = contenido,
    )
}
