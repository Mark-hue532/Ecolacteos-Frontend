package com.ecolacteos.acopio.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Los 7 enums del contrato (`MOBILE_DATA_MAPPING.md §1.6`, `PROMPT_FASE_02.md §1.c`): un valor no
 * reconocido decodifica a `UNKNOWN`, nunca lanza excepción -- exactamente el caso de `OBSERVADO`
 * (documentado pero sin productor hoy) y de cualquier valor de dominio futuro.
 */
class EnumsConReservaTest {

    private val json = Json

    @Serializable
    private data class Caja<T>(val valor: T)

    @Test
    fun `ResultadoCalidad -- valor conocido y desconocido`() {
        assertEquals(
            ResultadoCalidad.OBSERVADO,
            json.decodeFromString(Caja.serializer(ResultadoCalidad.serializer()), """{"valor":"OBSERVADO"}""").valor,
        )
        assertEquals(
            ResultadoCalidad.UNKNOWN,
            json.decodeFromString(Caja.serializer(ResultadoCalidad.serializer()), """{"valor":"VALOR_NUEVO"}""").valor,
        )
    }

    @Test
    fun `TipoClienteVenta -- valor conocido y desconocido`() {
        assertEquals(
            TipoClienteVenta.MAYORISTA,
            json.decodeFromString(Caja.serializer(TipoClienteVenta.serializer()), """{"valor":"MAYORISTA"}""").valor,
        )
        assertEquals(
            TipoClienteVenta.UNKNOWN,
            json.decodeFromString(Caja.serializer(TipoClienteVenta.serializer()), """{"valor":"mayorista"}""").valor,
        )
    }

    @Test
    fun `CicloCapital -- valor conocido y desconocido`() {
        assertEquals(
            CicloCapital.RAPIDO,
            json.decodeFromString(Caja.serializer(CicloCapital.serializer()), """{"valor":"RAPIDO"}""").valor,
        )
        assertEquals(
            CicloCapital.UNKNOWN,
            json.decodeFromString(Caja.serializer(CicloCapital.serializer()), """{"valor":"OTRO"}""").valor,
        )
    }

    @Test
    fun `EstadoConciliacion -- valor conocido y desconocido`() {
        assertEquals(
            EstadoConciliacion.ALERTA,
            json.decodeFromString(Caja.serializer(EstadoConciliacion.serializer()), """{"valor":"ALERTA"}""").valor,
        )
        assertEquals(
            EstadoConciliacion.UNKNOWN,
            json.decodeFromString(Caja.serializer(EstadoConciliacion.serializer()), """{"valor":"OTRO"}""").valor,
        )
    }

    @Test
    fun `Severidad -- valor conocido y desconocido`() {
        assertEquals(
            Severidad.ALTA,
            json.decodeFromString(Caja.serializer(Severidad.serializer()), """{"valor":"ALTA"}""").valor,
        )
        assertEquals(
            Severidad.UNKNOWN,
            json.decodeFromString(Caja.serializer(Severidad.serializer()), """{"valor":"CRITICA"}""").valor,
        )
    }

    @Test
    fun `TipoAlerta -- valor conocido y desconocido`() {
        assertEquals(
            TipoAlerta.RIESGO_ADULTERACION,
            json.decodeFromString(Caja.serializer(TipoAlerta.serializer()), """{"valor":"RIESGO_ADULTERACION"}""").valor,
        )
        assertEquals(
            TipoAlerta.UNKNOWN,
            json.decodeFromString(Caja.serializer(TipoAlerta.serializer()), """{"valor":"OTRA"}""").valor,
        )
    }

    @Test
    fun `Rol -- valor conocido y desconocido`() {
        assertEquals(
            Rol.ACOPIADOR,
            json.decodeFromString(Caja.serializer(Rol.serializer()), """{"valor":"ACOPIADOR"}""").valor,
        )
        assertEquals(
            Rol.UNKNOWN,
            json.decodeFromString(Caja.serializer(Rol.serializer()), """{"valor":"SUPERADMIN"}""").valor,
        )
    }
}
