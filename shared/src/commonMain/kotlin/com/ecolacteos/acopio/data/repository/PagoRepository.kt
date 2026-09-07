package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.data.remote.dto.PagoResponse
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.aResultadoDominio
import com.ecolacteos.acopio.domain.map
import com.ecolacteos.acopio.domain.model.Pago
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints

/**
 * `Pago` (`PROMPT_FASE_08E.md §4`, `R-04`) -- ONLINE-ONLY, **solo lectura** (RNF-12: el móvil no genera
 * pagos, `POST /api/pagos/generar` es WEB/ADMIN). Sin tabla local (`§11.3`).
 */
interface PagoRepository {
    suspend fun obtenerPorProveedor(proveedorId: String): ResultadoDominio<List<Pago>>
}

class PagoRepositoryImpl(private val apiClient: ApiClient) : PagoRepository {

    override suspend fun obtenerPorProveedor(proveedorId: String): ResultadoDominio<List<Pago>> =
        apiClient.get<List<PagoResponse>>(Endpoints.pagosPorProveedor(proveedorId))
            .aResultadoDominio().map { lista -> lista.map { it.aDominio() } }

    private fun PagoResponse.aDominio(): Pago = Pago(
        id = id,
        proveedorId = proveedorId,
        proveedorNombre = proveedorNombre,
        semanaInicio = semanaInicio,
        semanaFin = semanaFin,
        litrosTotales = litrosTotales,
        precioLitro = precioLitro,
        total = total,
        comprobanteGenerado = comprobanteGenerado,
    )
}
