package com.ecolacteos.acopio.presentation

import com.ecolacteos.acopio.core.ApiError

/**
 * Mensaje de error listo para pintar (`MOBILE_SCREENS.md §10.4`). El `UiState` nunca lleva un [ApiError]
 * crudo (`§3.3`) -- esto es lo que sí lleva.
 */
data class MensajeError(
    val texto: String,
    val reintentable: Boolean,
    val requiereReautenticacion: Boolean = false,
)

/**
 * Único mapeo de `ApiError` a mensaje de UI (`§10.4`), que todas las pantallas de esta fase y la Fase 8
 * reusan. `ApiError.mensaje` ya trae el texto del backend -- se muestra literal en 400/422/404/409
 * (regla: "el mensaje del backend se muestra literal", trampa #12) y se **reemplaza** por el texto fijo de
 * la tabla en los demás casos, donde §10.4 pide un texto propio de la app en vez del mensaje técnico.
 */
fun ApiError.aMensajeUi(): MensajeError = when (this) {
    is ApiError.SinConexion -> MensajeError(
        texto = "Sin conexión. Tu trabajo se guarda igual.",
        reintentable = true,
    )
    is ApiError.Timeout -> MensajeError(
        texto = "No pudimos conectarnos. Reintentamos solos en un momento.",
        reintentable = true,
    )
    is ApiError.ErrorServidor -> MensajeError(
        texto = "Algo salió mal del lado del servidor.",
        reintentable = true,
    )
    is ApiError.NoAutorizado -> MensajeError(
        texto = "Tu sesión venció, ingresá de nuevo",
        reintentable = false,
        requiereReautenticacion = true,
    )
    is ApiError.SinPermiso -> MensajeError(
        texto = "No tenés permiso para esta acción.",
        reintentable = false,
    )
    is ApiError.ErrorValidacion -> MensajeError(texto = mensaje, reintentable = false)
    is ApiError.NoEncontrado -> MensajeError(texto = mensaje, reintentable = false)
    is ApiError.Conflicto -> MensajeError(texto = mensaje, reintentable = false)
    is ApiError.Desconocido -> MensajeError(texto = mensaje, reintentable = false)
}
