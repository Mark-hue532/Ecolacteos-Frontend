package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.data.repository.NuevaVenta
import com.ecolacteos.acopio.data.repository.VentaRepository

/** Resultado de [CrearVentaUseCase] -- distingue el único rechazo que le toca a este UseCase (`§5`, `DATA-010`). */
sealed interface ResultadoCrearVenta {
    data class Creada(val uuidCliente: String) : ResultadoCrearVenta
    data object TipoClienteInvalido : ResultadoCrearVenta
}

/**
 * `§5`: "el UseCase sí debe rechazar un tipoCliente fuera del enum antes de tocar la red, no confiar en
 * que el 500 nunca llegue" (`DATA-010`: el backend hace `TipoClienteVenta.valueOf(...)` y devuelve 500, no
 * 400, ante un valor inválido). [TipoClienteVenta.UNKNOWN] es el único valor "fuera del enum real" que
 * puede llegar hasta acá -- es el fallback de deserialización de Fase 2, nunca un valor que la UI deba
 * poder enviar a propósito.
 */
class CrearVentaUseCase(private val repository: VentaRepository) {
    suspend operator fun invoke(datos: NuevaVenta): ResultadoCrearVenta {
        if (datos.tipoCliente == TipoClienteVenta.UNKNOWN) return ResultadoCrearVenta.TipoClienteInvalido
        return ResultadoCrearVenta.Creada(repository.crear(datos))
    }
}
