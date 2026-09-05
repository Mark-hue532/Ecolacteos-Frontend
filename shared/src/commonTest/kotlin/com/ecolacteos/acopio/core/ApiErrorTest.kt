package com.ecolacteos.acopio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifica `esTransitorio` contra la tabla de `docs/prompts/PROMPT_FASE_01.md` -- es la propiedad que el
 * Sync Engine de la Fase 5 usa para decidir si reintenta.
 */
class ApiErrorTest {

    @Test
    fun `sin conexion y timeout son transitorios`() {
        assertTrue(ApiError.SinConexion("sin red").esTransitorio)
        assertTrue(ApiError.Timeout("se agoto el tiempo").esTransitorio)
    }

    @Test
    fun `5xx es transitorio`() {
        assertTrue(ApiError.ErrorServidor("error interno", status = 500).esTransitorio)
        assertTrue(ApiError.ErrorServidor("gateway", status = 503).esTransitorio)
    }

    @Test
    fun `400 y 422 no son transitorios`() {
        assertFalse(ApiError.ErrorValidacion("dato invalido", status = 400).esTransitorio)
        assertFalse(ApiError.ErrorValidacion("regla de negocio", status = 422).esTransitorio)
    }

    @Test
    fun `401 no autorizado no es transitorio`() {
        assertFalse(ApiError.NoAutorizado("token invalido").esTransitorio)
    }

    @Test
    fun `403 sin permiso no es transitorio`() {
        assertFalse(ApiError.SinPermiso("rol insuficiente").esTransitorio)
    }

    @Test
    fun `404 no encontrado no es transitorio`() {
        assertFalse(ApiError.NoEncontrado("no existe").esTransitorio)
    }

    @Test
    fun `409 conflicto no es transitorio`() {
        assertFalse(ApiError.Conflicto("conflicto de conciliacion").esTransitorio)
    }

    @Test
    fun `desconocido no es transitorio por seguridad`() {
        assertFalse(ApiError.Desconocido("fallo no mapeado").esTransitorio)
    }

    @Test
    fun `cada variante conserva el mensaje literal del backend`() {
        val mensaje = "El proveedor no tiene ruta asignada"
        assertEquals(mensaje, ApiError.ErrorValidacion(mensaje, status = 422).mensaje)
    }
}
