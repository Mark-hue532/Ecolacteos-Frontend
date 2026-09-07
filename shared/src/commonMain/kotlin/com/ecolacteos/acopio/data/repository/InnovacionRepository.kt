package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.core.ApiError
import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.data.remote.dto.AlertaAnomaliaResponse
import com.ecolacteos.acopio.data.remote.dto.ScoreConfianzaResponse
import com.ecolacteos.acopio.domain.ErrorDominio
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.aErrorDominio
import com.ecolacteos.acopio.domain.aResultadoDominio
import com.ecolacteos.acopio.domain.map
import com.ecolacteos.acopio.domain.model.AlertaAnomalia
import com.ecolacteos.acopio.domain.model.ScoreConfianza
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints

/**
 * Resultado de `obtenerScore()` (`C-08`, `§6.4`): un `404` significa "este proveedor todavía no tiene
 * histórico" -- **no** es un fallo (trampa #7), así que no puede subir como [ErrorDominio] genérico o la
 * pantalla diría que algo salió mal cuando no pasó nada malo.
 */
sealed interface ResultadoScoreConfianza {
    data class Encontrado(val score: ScoreConfianza) : ResultadoScoreConfianza
    data object SinHistorico : ResultadoScoreConfianza
    data class Error(val error: ErrorDominio) : ResultadoScoreConfianza
}

/** `AlertaAnomalia`/`ScoreConfianza` (`PROMPT_FASE_08E.md §4`, CALIDAD) -- ONLINE-ONLY, sin tabla local (`§11.3`). */
interface InnovacionRepository {
    /** `zonaId` obligatorio -- nunca se llama sin él (trampa #8), lo exige quien lo invoca. */
    suspend fun obtenerAlertasPorZona(zonaId: String): ResultadoDominio<List<AlertaAnomalia>>
    suspend fun obtenerScore(proveedorId: String): ResultadoScoreConfianza
}

class InnovacionRepositoryImpl(private val apiClient: ApiClient) : InnovacionRepository {

    override suspend fun obtenerAlertasPorZona(zonaId: String): ResultadoDominio<List<AlertaAnomalia>> =
        apiClient.get<List<AlertaAnomaliaResponse>>(Endpoints.ALERTAS, mapOf("zonaId" to zonaId))
            .aResultadoDominio().map { lista -> lista.map { it.aDominio() } }

    override suspend fun obtenerScore(proveedorId: String): ResultadoScoreConfianza =
        when (val resultado = apiClient.get<ScoreConfianzaResponse>(Endpoints.scoreConfianza(proveedorId))) {
            is ApiResult.Exito -> ResultadoScoreConfianza.Encontrado(resultado.datos.aDominio())
            is ApiResult.Error -> {
                val error = resultado.error
                if (error is ApiError.NoEncontrado) {
                    ResultadoScoreConfianza.SinHistorico
                } else {
                    ResultadoScoreConfianza.Error(error.aErrorDominio())
                }
            }
        }

    private fun AlertaAnomaliaResponse.aDominio(): AlertaAnomalia = AlertaAnomalia(
        id = id,
        registroAcopioId = registroAcopioId,
        proveedorId = proveedorId,
        proveedorNombre = proveedorNombre,
        tipo = tipo,
        zScore = zScore,
        severidad = severidad,
        creadoEn = creadoEn,
    )

    private fun ScoreConfianzaResponse.aDominio(): ScoreConfianza = ScoreConfianza(
        proveedorId = proveedorId,
        periodo = periodo,
        score = score,
        componenteCalidad = componenteCalidad,
        componenteRegularidad = componenteRegularidad,
        componenteAnomalias = componenteAnomalias,
    )
}
