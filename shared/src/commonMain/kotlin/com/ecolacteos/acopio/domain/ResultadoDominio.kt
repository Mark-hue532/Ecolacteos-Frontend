package com.ecolacteos.acopio.domain

import com.ecolacteos.acopio.core.ApiResult

/**
 * Envoltorio de dominio equivalente a [ApiResult], pero con [ErrorDominio] en vez de `ApiError` (`§8`:
 * "el Repository nunca deja escapar ApiError tal cual hacia el UseCase"). Mismo nombre `Exito`/`Error` que
 * [ApiResult] a propósito -- ya es la convención de este proyecto (Fase 1/2), no hace falta inventar otra.
 */
sealed interface ResultadoDominio<out T> {
    data class Exito<T>(val datos: T) : ResultadoDominio<T>
    data class Error(val error: ErrorDominio) : ResultadoDominio<Nothing>
}

/** Traduce el resultado de una llamada de red al vocabulario de dominio -- ver [ErrorDominio.aErrorDominio]. */
fun <T> ApiResult<T>.aResultadoDominio(): ResultadoDominio<T> = when (this) {
    is ApiResult.Exito -> ResultadoDominio.Exito(datos)
    is ApiResult.Error -> ResultadoDominio.Error(error.aErrorDominio())
}

/** Transforma el dato de un [ResultadoDominio.Exito]; un [ResultadoDominio.Error] se propaga sin cambios. */
inline fun <T, R> ResultadoDominio<T>.map(transformar: (T) -> R): ResultadoDominio<R> = when (this) {
    is ResultadoDominio.Exito -> ResultadoDominio.Exito(transformar(datos))
    is ResultadoDominio.Error -> this
}
