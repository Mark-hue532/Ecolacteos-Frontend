package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.core.ApiError
import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.RecepcionPlantaRequest
import com.ecolacteos.acopio.data.remote.dto.RecepcionPlantaResponse
import com.ecolacteos.acopio.domain.ErrorDominio
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.aErrorDominio
import com.ecolacteos.acopio.domain.aResultadoDominio
import com.ecolacteos.acopio.domain.map
import com.ecolacteos.acopio.domain.model.RecepcionPlanta
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints
import kotlinx.datetime.LocalDate

/** Datos que la UI ya validó para `R-01` (`§5`). [turno] `null` = el usuario lo dejó vacío -- nunca se fuerza a `"UNICO"` en el cliente (trampa #3). */
data class NuevaRecepcionPlanta(
    val fecha: LocalDate,
    val turno: String?,
    val unidadId: String,
    val litrosCampo: Decimal,
    val litrosPlanta: Decimal,
)

/**
 * Resultado de `registrar()` (`R-01`/`R-02b`, `MOBILE_ARCHITECTURE.md §8`): el `409` es el único conflicto
 * real del sistema -- clave natural `(fecha, unidadId, turno)` duplicada -- y **no** es un
 * [ErrorDominio] genérico (mismo criterio que `ResultadoCrearHijo` de Fase 6, `§4.2.a`): la UI necesita
 * distinguirlo para mostrar el flujo de `R-02b`, no un mensaje de error cualquiera.
 */
sealed interface ResultadoRegistrarRecepcion {
    data class Creada(val recepcion: RecepcionPlanta) : ResultadoRegistrarRecepcion
    data object YaExiste : ResultadoRegistrarRecepcion
    data class Error(val error: ErrorDominio) : ResultadoRegistrarRecepcion
}

/**
 * `RecepcionPlanta` (`PROMPT_FASE_08E.md §4`) -- ONLINE-ONLY sin cola, mismo patrón que
 * `CorreccionRegistroRepository` (Fase 6 `§4.4`): sin tabla local, sin `uuidCliente`, sin reintento
 * propio. `§18.3`: el backend no es idempotente para este recurso -- encolarlo produciría duplicados
 * reales (trampa #1).
 */
interface RecepcionPlantaRepository {
    suspend fun registrar(datos: NuevaRecepcionPlanta): ResultadoRegistrarRecepcion

    /** `R-03` e búsqueda de `R-02b` -- un solo método, dos consumidores (trampa #12). `unidadId` opcional. */
    suspend fun buscarPorUnidad(unidadId: String? = null): ResultadoDominio<List<RecepcionPlanta>>

    /** `R-02` cuando se llega desde `R-03` (por id) en vez de recién creada. */
    suspend fun obtenerDetalle(id: String): ResultadoDominio<RecepcionPlanta>
}

class RecepcionPlantaRepositoryImpl(private val apiClient: ApiClient) : RecepcionPlantaRepository {

    override suspend fun registrar(datos: NuevaRecepcionPlanta): ResultadoRegistrarRecepcion {
        val request = RecepcionPlantaRequest(
            fecha = datos.fecha,
            turno = datos.turno,
            unidadId = datos.unidadId,
            litrosCampo = datos.litrosCampo,
            litrosPlanta = datos.litrosPlanta,
        )
        return when (val resultado = apiClient.post<RecepcionPlantaRequest, RecepcionPlantaResponse>(Endpoints.RECEPCION_PLANTA, request)) {
            is ApiResult.Exito -> ResultadoRegistrarRecepcion.Creada(resultado.datos.aDominio())
            is ApiResult.Error -> {
                val error = resultado.error
                if (error is ApiError.Conflicto) {
                    ResultadoRegistrarRecepcion.YaExiste
                } else {
                    ResultadoRegistrarRecepcion.Error(error.aErrorDominio())
                }
            }
        }
    }

    override suspend fun buscarPorUnidad(unidadId: String?): ResultadoDominio<List<RecepcionPlanta>> =
        apiClient.get<List<RecepcionPlantaResponse>>(
            Endpoints.RECEPCION_PLANTA,
            if (unidadId != null) mapOf("unidadId" to unidadId) else emptyMap(),
        ).aResultadoDominio().map { lista -> lista.map { it.aDominio() } }

    override suspend fun obtenerDetalle(id: String): ResultadoDominio<RecepcionPlanta> =
        apiClient.get<RecepcionPlantaResponse>(Endpoints.recepcionPlantaPorId(id)).aResultadoDominio().map { it.aDominio() }

    private fun RecepcionPlantaResponse.aDominio(): RecepcionPlanta = RecepcionPlanta(
        id = id,
        fecha = fecha,
        turno = turno,
        unidadId = unidadId,
        litrosCampo = litrosCampo,
        litrosPlanta = litrosPlanta,
        diferenciaPct = diferenciaPct,
        estado = estado,
        litrosRegistradosAcopio = litrosRegistradosAcopio,
    )
}
