package com.ecolacteos.acopio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiResultTest {

    private val errorDePrueba = ApiError.NoEncontrado("no encontrado")

    @Test
    fun `map transforma el dato en exito`() {
        val resultado: ApiResult<Int> = ApiResult.Exito(2)
        val transformado = resultado.map { it * 10 }
        assertEquals(ApiResult.Exito(20), transformado)
    }

    @Test
    fun `map no toca un error`() {
        val resultado: ApiResult<Int> = ApiResult.Error(errorDePrueba)
        val transformado = resultado.map { it * 10 }
        assertTrue(transformado is ApiResult.Error)
        assertEquals(errorDePrueba, transformado.error)
    }

    @Test
    fun `flatMap encadena otro resultado en exito`() {
        val resultado: ApiResult<Int> = ApiResult.Exito(2)
        val encadenado = resultado.flatMap { ApiResult.Exito(it + 1) }
        assertEquals(ApiResult.Exito(3), encadenado)
    }

    @Test
    fun `flatMap no ejecuta la funcion sobre un error`() {
        val resultado: ApiResult<Int> = ApiResult.Error(errorDePrueba)
        var seEjecuto = false
        val encadenado = resultado.flatMap {
            seEjecuto = true
            ApiResult.Exito(it + 1)
        }
        assertTrue(!seEjecuto)
        assertTrue(encadenado is ApiResult.Error)
    }

    @Test
    fun `onExito se ejecuta solo en exito`() {
        var capturado: Int? = null
        (ApiResult.Exito(5) as ApiResult<Int>).onExito { capturado = it }
        assertEquals(5, capturado)

        var noDeberiaCambiar: Int? = null
        (ApiResult.Error(errorDePrueba) as ApiResult<Int>).onExito { noDeberiaCambiar = it }
        assertNull(noDeberiaCambiar)
    }

    @Test
    fun `onError se ejecuta solo en error`() {
        var capturado: ApiError? = null
        (ApiResult.Error(errorDePrueba) as ApiResult<Int>).onError { capturado = it }
        assertEquals(errorDePrueba, capturado)

        var noDeberiaCambiar: ApiError? = null
        (ApiResult.Exito(5) as ApiResult<Int>).onError { noDeberiaCambiar = it }
        assertNull(noDeberiaCambiar)
    }

    @Test
    fun `getOrNull devuelve el dato en exito y null en error`() {
        assertEquals(5, (ApiResult.Exito(5) as ApiResult<Int>).getOrNull())
        assertNull((ApiResult.Error(errorDePrueba) as ApiResult<Int>).getOrNull())
    }

    @Test
    fun `getOrElse devuelve el dato en exito y el fallback en error`() {
        assertEquals(5, (ApiResult.Exito(5) as ApiResult<Int>).getOrElse { -1 })
        assertEquals(-1, (ApiResult.Error(errorDePrueba) as ApiResult<Int>).getOrElse { -1 })
    }
}
