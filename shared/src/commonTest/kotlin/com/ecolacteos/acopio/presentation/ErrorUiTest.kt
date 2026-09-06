package com.ecolacteos.acopio.presentation

import com.ecolacteos.acopio.core.ApiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test 9 de `PROMPT_FASE_07.md §9`: cada código HTTP produce el mensaje y la reintentabilidad de `§10.4`. */
class ErrorUiTest {

    @Test
    fun `sin conexion es reintentable y no exige reautenticacion`() {
        val mensaje = ApiError.SinConexion("no importa").aMensajeUi()
        assertEquals("Sin conexión. Tu trabajo se guarda igual.", mensaje.texto)
        assertTrue(mensaje.reintentable)
        assertFalse(mensaje.requiereReautenticacion)
    }

    @Test
    fun `timeout es reintentable`() {
        val mensaje = ApiError.Timeout("no importa").aMensajeUi()
        assertEquals("No pudimos conectarnos. Reintentamos solos en un momento.", mensaje.texto)
        assertTrue(mensaje.reintentable)
    }

    @Test
    fun `5xx es reintentable`() {
        val mensaje = ApiError.ErrorServidor("no importa", status = 500).aMensajeUi()
        assertEquals("Algo salió mal del lado del servidor.", mensaje.texto)
        assertTrue(mensaje.reintentable)
    }

    @Test
    fun `401 exige cierre de sesion y no es reintentable`() {
        val mensaje = ApiError.NoAutorizado("no importa").aMensajeUi()
        assertEquals("Tu sesión venció, ingresá de nuevo", mensaje.texto)
        assertFalse(mensaje.reintentable)
        assertTrue(mensaje.requiereReautenticacion)
    }

    @Test
    fun `403 no es reintentable`() {
        val mensaje = ApiError.SinPermiso("no importa").aMensajeUi()
        assertEquals("No tenés permiso para esta acción.", mensaje.texto)
        assertFalse(mensaje.reintentable)
    }

    @Test
    fun `400 y 422 muestran el mensaje del backend literal y no son reintentables`() {
        val mensaje = ApiError.ErrorValidacion("El proveedor está inactivo", status = 422).aMensajeUi()
        assertEquals("El proveedor está inactivo", mensaje.texto)
        assertFalse(mensaje.reintentable)
    }

    @Test
    fun `404 muestra el mensaje del backend y no es reintentable`() {
        val mensaje = ApiError.NoEncontrado("No se encontró el registro").aMensajeUi()
        assertEquals("No se encontró el registro", mensaje.texto)
        assertFalse(mensaje.reintentable)
    }

    @Test
    fun `409 muestra el mensaje del backend y no es reintentable`() {
        val mensaje = ApiError.Conflicto("Ya existe una recepción para este registro").aMensajeUi()
        assertEquals("Ya existe una recepción para este registro", mensaje.texto)
        assertFalse(mensaje.reintentable)
    }
}
