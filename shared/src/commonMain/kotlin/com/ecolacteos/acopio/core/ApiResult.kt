package com.ecolacteos.acopio.core

/**
 * Envoltorio de todo resultado que puede fallar al cruzar la capa de red o de sincronización.
 * La UI y los UseCases nunca ven excepciones de Ktor: todo llega como [Exito] o [Error]
 * (ver MOBILE_ARCHITECTURE.md §10 y CLAUDE.md §3.4).
 */
sealed interface ApiResult<out T> {
    data class Exito<T>(val datos: T) : ApiResult<T>
    data class Error(val error: ApiError) : ApiResult<Nothing>
}

/** Transforma el dato de un [ApiResult.Exito]; un [ApiResult.Error] se propaga sin cambios. */
inline fun <T, R> ApiResult<T>.map(transformar: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Exito -> ApiResult.Exito(transformar(datos))
    is ApiResult.Error -> this
}

/** Encadena otra operación que también puede fallar; un [ApiResult.Error] se propaga sin ejecutar [transformar]. */
inline fun <T, R> ApiResult<T>.flatMap(transformar: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Exito -> transformar(datos)
    is ApiResult.Error -> this
}

/** Ejecuta [accion] solo si el resultado es [ApiResult.Exito]. Devuelve el mismo resultado, para encadenar. */
inline fun <T> ApiResult<T>.onExito(accion: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Exito) accion(datos)
    return this
}

/** Ejecuta [accion] solo si el resultado es [ApiResult.Error]. Devuelve el mismo resultado, para encadenar. */
inline fun <T> ApiResult<T>.onError(accion: (ApiError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) accion(error)
    return this
}

/** Devuelve el dato en éxito, o `null` en error. */
fun <T> ApiResult<T>.getOrNull(): T? = when (this) {
    is ApiResult.Exito -> datos
    is ApiResult.Error -> null
}

/** Devuelve el dato en éxito, o el resultado de [porDefecto] en error. */
inline fun <T> ApiResult<T>.getOrElse(porDefecto: (ApiError) -> T): T = when (this) {
    is ApiResult.Exito -> datos
    is ApiResult.Error -> porDefecto(error)
}
