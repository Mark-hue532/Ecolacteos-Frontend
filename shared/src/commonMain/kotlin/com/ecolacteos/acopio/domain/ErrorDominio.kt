package com.ecolacteos.acopio.domain

import com.ecolacteos.acopio.core.ApiError

/**
 * Error de dominio para las operaciones **online-only** de esta fase (`PROMPT_FASE_06.md §8`) --
 * `CorreccionRegistroRepository`, `ComunicadoConfirmacionRepository`, `CatalogoRepository.refrescar()`, y
 * la resolución de padre ajeno de §4.2. Los Repository/UseCase offline-first (`crear()` de los 4 recursos
 * con cola) **no** producen esto: escriben en SQLite y listo, el Sync Engine absorbe cualquier fallo de
 * red como `FAILED`-transitorio sin que llegue nunca hasta acá (§8, literal).
 *
 * Nunca reexporta [ApiError] tal cual -- ver [aErrorDominio] para el mapeo exacto.
 */
sealed class ErrorDominio(open val mensaje: String) {

    /** 401, o refresh ya fallido -- nunca se resuelve reintentando solo, exige volver a loguearse. */
    data class RequiereReautenticacion(override val mensaje: String) : ErrorDominio(mensaje)

    /** 403 -- el rol de la sesión activa no alcanza para esta operación. */
    data class SinPermiso(override val mensaje: String) : ErrorDominio(mensaje)

    /** 400/404/409/422 -- error de negocio o de validación, mostrable tal cual, no se reintenta solo. */
    data class Permanente(override val mensaje: String) : ErrorDominio(mensaje)

    /** 5xx/timeout/sin conectividad -- el llamador puede ofrecer "reintentar", nunca se reintenta solo acá. */
    data class Transitorio(override val mensaje: String) : ErrorDominio(mensaje)

    /** Cualquier otra forma de fallo no contemplada arriba. */
    data class Desconocido(override val mensaje: String) : ErrorDominio(mensaje)
}

/**
 * Mapeo único `ApiError → ErrorDominio` (`§8`): 401→reautenticación, 403→sin permiso, 400/404/409/422→
 * permanente, 5xx/timeout/sin conexión→transitorio. Un solo lugar para no repetir este `when` en cada
 * Repository online-only.
 */
fun ApiError.aErrorDominio(): ErrorDominio = when (this) {
    is ApiError.NoAutorizado -> ErrorDominio.RequiereReautenticacion(mensaje)
    is ApiError.SinPermiso -> ErrorDominio.SinPermiso(mensaje)
    is ApiError.ErrorValidacion -> ErrorDominio.Permanente(mensaje)
    is ApiError.NoEncontrado -> ErrorDominio.Permanente(mensaje)
    is ApiError.Conflicto -> ErrorDominio.Permanente(mensaje)
    is ApiError.SinConexion -> ErrorDominio.Transitorio(mensaje)
    is ApiError.Timeout -> ErrorDominio.Transitorio(mensaje)
    is ApiError.ErrorServidor -> ErrorDominio.Transitorio(mensaje)
    is ApiError.Desconocido -> ErrorDominio.Desconocido(mensaje)
}
