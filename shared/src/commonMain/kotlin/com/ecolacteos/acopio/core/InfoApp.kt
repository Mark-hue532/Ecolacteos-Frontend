package com.ecolacteos.acopio.core

/**
 * Versión de la app para `S-07 · Ajustes` (`MOBILE_SCREENS.md §4`, decisión #2 de `PROMPT_FASE_08B.md §7`).
 *
 * Decisión tomada: constante de `shared/`, mantenida a mano, en vez de un `expect`/`actual` que lea
 * `BuildConfig.VERSION_NAME` (Android, requiere habilitar `buildFeatures.buildConfig` -- hoy apagado) o
 * `NSBundle.mainBundle().infoDictionary` (iOS). La opción `expect`/`actual` es más correcta (una sola
 * fuente de verdad, la del instalador real) pero agrega dos `actual` más y su stub de `jvmMain` para un
 * campo de solo lectura en una pantalla de diagnóstico -- desproporcionado para esta sub-fase. Si el
 * versionado real importa (ej. para soporte técnico en campo), vale la pena revisar esto en una fase
 * posterior; mientras tanto, quien suba `androidApp/build.gradle.kts:versionName` actualiza esta constante
 * a mano en el mismo commit.
 */
const val VERSION_APP: String = "0.1.0-fase8b"
