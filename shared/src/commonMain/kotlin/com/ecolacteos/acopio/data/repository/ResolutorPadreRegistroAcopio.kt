package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.data.local.datasource.RegistroAcopioCacheLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RegistroAcopioLocalDataSource
import com.ecolacteos.acopio.data.remote.dto.RegistroAcopioResponse
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.Endpoints
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Referencia al `RegistroAcopio` padre que la UI ya resolvió antes de llamar a `crear()`
 * (`PROMPT_FASE_06.md §4.2`): **propio** -- el usuario eligió un registro de su propio backlog local
 * (`uuidCliente`); **ajeno** -- el usuario eligió un registro de OTRO dispositivo, con su id de servidor ya
 * conocido (típicamente porque vino de `ObtenerRegistrosDeProveedorUseCase`, que lo dejó en
 * `registro_acopio_cache`).
 */
sealed interface ReferenciaRegistroAcopio {
    data class Propio(val uuidCliente: String) : ReferenciaRegistroAcopio
    data class Ajeno(val serverId: String) : ReferenciaRegistroAcopio
}

/**
 * Resultado de crear un `AnalisisCalidad`/`LoteProduccion` (`§4.2.a`). [Creado] es éxito en los dos
 * sentidos posibles: el hijo puede entrar directo a `PENDING` (padre ya resuelto) o quedar en
 * `PENDING_DEPENDENCY` (padre propio sin sincronizar todavía -- se resuelve solo, §18.1 mecanismo 1).
 * [PadreAjenoNoResolubleSinConectividad] es el único caso que bloquea la creación por completo: un padre
 * ajeno sin cachear y sin red para ir a buscarlo -- "el caso que no cubre ninguna de las dos" de §18.1, no
 * hay ninguna referencia que permita diferirlo con seguridad.
 */
sealed interface ResultadoCrearHijo {
    data class Creado(val uuidCliente: String) : ResultadoCrearHijo
    data object PadreAjenoNoResolubleSinConectividad : ResultadoCrearHijo
}

/**
 * Lo que decide el resolutor para una referencia: **exactamente uno** de los dos campos no-nulo, mismo
 * invariante C-02 que ya exige el `CHECK`/`init{}` de `AnalisisCalidad`/`LoteProduccionRegistro` (Fase 4) --
 * el resolutor arma directamente el par que la fila hija va a persistir.
 */
internal sealed interface ResolucionPadre {
    data class Registrable(val registroAcopioUuidCliente: String?, val registroAcopioServerId: String?) : ResolucionPadre
    data object SinConectividadParaResolver : ResolucionPadre
}

/**
 * Máquina de decisión compartida de `§4.2` -- ni `AnalisisCalidadRepositoryImpl` ni
 * `LoteProduccionRepositoryImpl` la duplican, los dos necesitan exactamente la misma:
 *
 * 1. Propio: si `registro_acopio_local.server_id` ya está presente, resuelto ahí mismo (mecanismo 1, hoy
 *    casi nunca por `DATA-014`); si no, se difiere -- el hijo nace `PENDING_DEPENDENCY` y el Sync Engine lo
 *    promueve solo cuando el padre sincronice. Ningún llamado de red en esta rama.
 * 2. Ajeno ya cacheado: resuelto directo contra `registro_acopio_cache` (mecanismo 2) -- tampoco hay
 *    llamado de red, el `serverId` que trae la referencia ya es válido de por sí, la fila de cache solo
 *    confirma que salió de un flujo legítimo (`ObtenerRegistrosDeProveedorUseCase`).
 * 3. Ajeno no cacheado todavía: único caso con un llamado de red real, `GET /api/registros-acopio/{id}`
 *    (el detalle, no el listado por proveedor -- ya se conoce el id puntual). Si falla, no se escribe nada:
 *    ver [ResultadoCrearHijo.PadreAjenoNoResolubleSinConectividad].
 */
internal class ResolutorPadreRegistroAcopio(
    private val registrosLocal: RegistroAcopioLocalDataSource,
    private val cacheLocal: RegistroAcopioCacheLocalDataSource,
    private val apiClient: ApiClient,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend fun resolver(referencia: ReferenciaRegistroAcopio): ResolucionPadre = when (referencia) {
        is ReferenciaRegistroAcopio.Propio -> resolverPropio(referencia.uuidCliente)
        is ReferenciaRegistroAcopio.Ajeno -> resolverAjeno(referencia.serverId)
    }

    private fun resolverPropio(uuidCliente: String): ResolucionPadre {
        val serverId = registrosLocal.obtenerPorUuidCliente(uuidCliente)?.serverId
        return if (serverId != null) {
            ResolucionPadre.Registrable(registroAcopioUuidCliente = null, registroAcopioServerId = serverId)
        } else {
            ResolucionPadre.Registrable(registroAcopioUuidCliente = uuidCliente, registroAcopioServerId = null)
        }
    }

    private suspend fun resolverAjeno(serverId: String): ResolucionPadre {
        if (cacheLocal.obtenerPorServerId(serverId) != null) {
            return ResolucionPadre.Registrable(registroAcopioUuidCliente = null, registroAcopioServerId = serverId)
        }

        // Caso 3 de §4.2: no cacheado todavía. Se intenta poblar por id antes de rendirse -- si esto falla
        // (sin conectividad, timeout, lo que sea) NO se escribe nada, nunca un estado que sugiera espera.
        return when (val respuesta = apiClient.get<RegistroAcopioResponse>(Endpoints.registroAcopioPorId(serverId))) {
            is ApiResult.Exito -> {
                cacheLocal.upsert(respuesta.datos.aReferenciaCacheDeDetalle())
                ResolucionPadre.Registrable(registroAcopioUuidCliente = null, registroAcopioServerId = serverId)
            }
            is ApiResult.Error -> ResolucionPadre.SinConectividadParaResolver
        }
    }

    private fun RegistroAcopioResponse.aReferenciaCacheDeDetalle(): RegistroAcopioReferencia = RegistroAcopioReferencia(
        id = id,
        uuidCliente = uuidCliente,
        proveedorId = proveedorId,
        proveedorNombre = proveedorNombre,
        fechaHora = fechaHora,
        litros = litros,
        tieneObservacion = null, // solo lo trae el DTO resumen (DATA-013), no el de detalle
        origen = Origen.DETALLE,
        actualizadoEn = ahoraComoFechaHora(reloj, zona),
    )
}
