package com.ecolacteos.acopio.core

/**
 * Jerarquía cerrada de todo fallo que puede producir la capa de red o el Sync Engine.
 * Se deriva de dos fuentes: los códigos HTTP reales del backend (`MOBILE_DATA_MAPPING.md §9`) y los
 * fallos de red que no llegan a tener código (sin conectividad, timeout).
 *
 * [esTransitorio] es la propiedad que usa el Sync Engine (Fase 5) para decidir si reintenta una operación
 * pendiente o la marca como fallida permanente -- ver la tabla en `docs/prompts/PROMPT_FASE_01.md`.
 * [mensaje] siempre lleva el texto que vino del backend (o uno propio del cliente si nunca hubo respuesta),
 * porque `MOBILE_SCREENS.md §10.4` exige mostrarlo literal en los errores de validación.
 */
sealed class ApiError(val mensaje: String, val esTransitorio: Boolean) {

    /** No hay conectividad -- la request nunca salió del dispositivo. */
    class SinConexion(mensaje: String) : ApiError(mensaje, esTransitorio = true)

    /** La request salió pero no hubo respuesta a tiempo. */
    class Timeout(mensaje: String) : ApiError(mensaje, esTransitorio = true)

    /** 5xx -- servidor caído o error interno. */
    class ErrorServidor(mensaje: String, val status: Int) : ApiError(mensaje, esTransitorio = true)

    /** 400/422 -- validación o regla de negocio; el usuario debe corregir. */
    class ErrorValidacion(mensaje: String, val status: Int) : ApiError(mensaje, esTransitorio = false)

    /** 401 -- no autorizado; dispara el flujo de re-login. */
    class NoAutorizado(mensaje: String) : ApiError(mensaje, esTransitorio = false)

    /** 403 -- el rol del usuario no alcanza para la operación. */
    class SinPermiso(mensaje: String) : ApiError(mensaje, esTransitorio = false)

    /** 404 -- recurso no encontrado. */
    class NoEncontrado(mensaje: String) : ApiError(mensaje, esTransitorio = false)

    /**
     * 409 -- conflicto. Hoy solo lo devuelve `RecepcionPlanta`
     * (ver `MOBILE_ARCHITECTURE.md §18.3`). Nunca se reintenta a ciegas: requiere manejo específico
     * de la pantalla o del Sync Engine.
     */
    class Conflicto(mensaje: String) : ApiError(mensaje, esTransitorio = false)

    /** Cualquier código o forma de fallo no contemplada arriba. Por seguridad, nunca se reintenta a ciegas. */
    class Desconocido(mensaje: String, val status: Int? = null) : ApiError(mensaje, esTransitorio = false)
}
