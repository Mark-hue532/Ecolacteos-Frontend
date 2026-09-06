package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.data.remote.dto.ComunicadoConfirmacionResponse
import com.ecolacteos.acopio.data.remote.dto.ConfirmarComunicadoRequest
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.aResultadoDominio
import com.ecolacteos.acopio.domain.map
import com.ecolacteos.acopio.domain.model.ComunicadoConfirmacion
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints

/**
 * ONLINE-ONLY, sin cola (`PROMPT_FASE_06.md §4.4`, `§11.3`): `ComunicadoConfirmacion` no tiene tabla local
 * ni `uuidCliente` (`DATA-005`, `§18.2`). Debería ser offline-first (es exactamente la clase de acción que
 * un ACOPIADOR hace en campo sin señal), pero el backend no es idempotente todavía -- encolarla generaría
 * confirmaciones duplicadas reales (trampa #9). Se deja tal cual está hoy, sin agregar cola propia.
 */
interface ComunicadoConfirmacionRepository {
    suspend fun confirmar(comunicadoId: String, proveedorId: String): ResultadoDominio<ComunicadoConfirmacion>
}

class ComunicadoConfirmacionRepositoryImpl(
    private val apiClient: ApiClient,
) : ComunicadoConfirmacionRepository {

    override suspend fun confirmar(comunicadoId: String, proveedorId: String): ResultadoDominio<ComunicadoConfirmacion> =
        apiClient.post<ConfirmarComunicadoRequest, ComunicadoConfirmacionResponse>(
            Endpoints.confirmarComunicado(comunicadoId),
            ConfirmarComunicadoRequest(proveedorId = proveedorId),
        ).aResultadoDominio().map { it.aDominio() }

    private fun ComunicadoConfirmacionResponse.aDominio(): ComunicadoConfirmacion = ComunicadoConfirmacion(
        id = id,
        proveedorId = proveedorId,
        proveedorNombre = proveedorNombre,
        acopiadorId = acopiadorId,
        acopiadorNombre = acopiadorNombre,
        confirmadoEn = confirmadoEn,
    )
}
