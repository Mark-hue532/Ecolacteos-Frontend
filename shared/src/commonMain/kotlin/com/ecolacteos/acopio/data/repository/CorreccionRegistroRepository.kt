package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.CorreccionRegistroRequest
import com.ecolacteos.acopio.data.remote.dto.CorreccionRegistroResponse
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.aResultadoDominio
import com.ecolacteos.acopio.domain.map
import com.ecolacteos.acopio.domain.model.CorreccionRegistro
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints

/**
 * ONLINE-ONLY, sin cola (`PROMPT_FASE_06.md §4.4`, `§11.3`): `CorreccionRegistro` no tiene tabla local ni
 * `uuidCliente` (`DATA-004`). Sin conectividad, el error sube tal cual a la UI como
 * [com.ecolacteos.acopio.domain.ErrorDominio.Transitorio] -- nada de cola ni reintento propio (trampa #9):
 * `§18.7` documenta que el backend no es idempotente para esto todavía, encolarlo generaría duplicados
 * reales (cada `POST` crea una fila nueva, no hay `registrarOIgnorarSiDuplicado`).
 */
interface CorreccionRegistroRepository {
    suspend fun anexar(registroAcopioId: String, litrosCorregido: Decimal, motivo: String?): ResultadoDominio<CorreccionRegistro>
}

class CorreccionRegistroRepositoryImpl(
    private val apiClient: ApiClient,
) : CorreccionRegistroRepository {

    override suspend fun anexar(
        registroAcopioId: String,
        litrosCorregido: Decimal,
        motivo: String?,
    ): ResultadoDominio<CorreccionRegistro> =
        apiClient.post<CorreccionRegistroRequest, CorreccionRegistroResponse>(
            Endpoints.correccionesDeRegistro(registroAcopioId),
            CorreccionRegistroRequest(litrosCorregido = litrosCorregido, motivo = motivo),
        ).aResultadoDominio().map { it.aDominio() }

    private fun CorreccionRegistroResponse.aDominio(): CorreccionRegistro = CorreccionRegistro(
        id = id,
        registroAcopioId = registroAcopioId,
        litrosAnterior = litrosAnterior,
        litrosCorregido = litrosCorregido,
        motivo = motivo,
        usuarioNombre = usuarioNombre,
        creadoEn = creadoEn,
    )
}
