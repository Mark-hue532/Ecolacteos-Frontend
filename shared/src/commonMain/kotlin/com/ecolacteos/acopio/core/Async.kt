package com.ecolacteos.acopio.core

/**
 * Envoltorio de carga asíncrona para toda lectura remota (`MOBILE_SCREENS.md §3.2`). Todas las pantallas
 * manejan los mismos cuatro casos, así ninguna se olvida de [Fallo] con datos previos: en una app
 * offline-first casi nunca hay una pantalla "vacía por error" -- normalmente tiene datos viejos de SQLite
 * más un aviso de que no se pudo refrescar. Tapar esos datos con una pantalla de error a pantalla completa
 * es el antipatrón que este tipo evita.
 *
 * Solo el tipo en esta fase, sin uso todavía -- lo consumen los ViewModels de la Fase 7.
 */
sealed interface Async<out T> {
    data object Inicial : Async<Nothing>
    data object Cargando : Async<Nothing>
    data class Exito<T>(val datos: T, val desdeCache: Boolean) : Async<T>
    data class Fallo(val error: ApiError, val datosPrevios: Any? = null) : Async<Nothing>
}
