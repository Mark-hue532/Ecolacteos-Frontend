package com.ecolacteos.acopio.synchronization

import com.ecolacteos.acopio.data.remote.dto.AnalisisCalidadRequest
import com.ecolacteos.acopio.data.remote.dto.CambiosResponse
import com.ecolacteos.acopio.data.remote.dto.CrearLoteRequest
import com.ecolacteos.acopio.data.remote.dto.RegistroAcopioDTO
import com.ecolacteos.acopio.data.remote.dto.VentaRequest
import com.ecolacteos.acopio.domain.model.AnalisisCalidad
import com.ecolacteos.acopio.domain.model.Comunicado
import com.ecolacteos.acopio.domain.model.LoteProduccion
import com.ecolacteos.acopio.domain.model.MotivoObservacion
import com.ecolacteos.acopio.domain.model.PrediccionProveedor
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.TipoQueso
import com.ecolacteos.acopio.domain.model.Unidad
import com.ecolacteos.acopio.domain.model.Venta
import kotlinx.datetime.LocalDateTime

/**
 * Dominio → DTO de request de los 4 endpoints `POST /api/sync/{recurso}`, y `CambiosResponse` → modelos de
 * catálogo. Es el único lugar de esta fase donde `synchronization/` toca `data/remote/dto/`.
 *
 * `fotoUrl` no se envía nunca (C-07: no hay evidencia fotográfica en v1, y `DATA-008` documenta que no
 * existe endpoint de subida detrás) -- se deja en su default `null` en vez de inventar un valor.
 */
internal fun RegistroAcopio.aRequest(): RegistroAcopioDTO = RegistroAcopioDTO(
    uuidCliente = uuidCliente,
    proveedorId = proveedorId,
    unidadId = unidadId,
    fechaHora = fechaHora,
    litros = litros,
    gpsLat = gpsLat,
    gpsLng = gpsLng,
    motivoObservacionId = motivoObservacionId,
    litrosPorVoz = litrosPorVoz,
)

/**
 * [registroAcopioId] es el id de **servidor** del padre, ya resuelto por §18.1 -- por eso entra por
 * parámetro en vez de leerse del modelo: la fila local guarda `uuid_cliente` o `server_id` del padre
 * (C-02), nunca directamente el id que este request necesita.
 */
internal fun AnalisisCalidad.aRequest(registroAcopioId: String): AnalisisCalidadRequest = AnalisisCalidadRequest(
    uuidCliente = uuidCliente,
    registroAcopioId = registroAcopioId,
    folioMuestra = folioMuestra,
    agua = agua,
    proteina = proteina,
    lactosa = lactosa,
    densidad = densidad,
    temperatura = temperatura,
    ph = ph,
    aguaAnadida = aguaAnadida,
)

/** [registroAcopioIds] ya resueltos a ids de servidor -- todos o ninguno (un lote a medias cambia su significado). */
internal fun LoteProduccion.aRequest(registroAcopioIds: List<String>): CrearLoteRequest = CrearLoteRequest(
    uuidCliente = uuidCliente,
    fecha = fecha,
    tipoQuesoId = tipoQuesoId,
    litrosUsados = litrosUsados,
    unidadesObtenidas = unidadesObtenidas,
    registroAcopioIds = registroAcopioIds,
)

internal fun Venta.aRequest(): VentaRequest = VentaRequest(
    uuidCliente = uuidCliente,
    fecha = fecha,
    tipoCliente = tipoCliente,
    tipoQuesoId = tipoQuesoId,
    cantidad = cantidad,
    precioUnitario = precioUnitario,
)

/**
 * `CambiosResponse` → modelos de catálogo. [actualizadoEn] es la hora de pared **del dispositivo** al
 * escribir la fila, no `generadoEn` de la respuesta: decisión ya tomada y documentada en la Fase 4
 * (mantiene todas las columnas de fecha del esquema local en el mismo marco temporal, y la retención de
 * §11.4 compara contra un umbral calculado con ese mismo reloj). `generadoEn` es el único `Instant` del
 * contrato y no tiene columna donde persistirse -- no se inventa una.
 */
internal fun CambiosResponse.aProveedores(actualizadoEn: LocalDateTime): List<Proveedor> = proveedores.map {
    Proveedor(
        id = it.id,
        nombre = it.nombre,
        zonaActualId = it.zonaActualId,
        zonaActualNombre = it.zonaActualNombre,
        codigoQr = it.codigoQr,
        actualizadoEn = actualizadoEn,
    )
}

internal fun CambiosResponse.aUnidades(actualizadoEn: LocalDateTime): List<Unidad> = unidades.map {
    Unidad(
        id = it.id,
        placa = it.placa,
        capacidadTon = it.capacidadTon,
        zonaId = it.zonaId,
        responsableId = it.responsableId,
        responsableNombre = it.responsableNombre,
        actualizadoEn = actualizadoEn,
    )
}

internal fun CambiosResponse.aMotivosObservacion(actualizadoEn: LocalDateTime): List<MotivoObservacion> =
    motivosObservacion.map {
        MotivoObservacion(id = it.id, descripcion = it.descripcion, actualizadoEn = actualizadoEn)
    }

internal fun CambiosResponse.aTiposQueso(actualizadoEn: LocalDateTime): List<TipoQueso> = tiposQueso.map {
    TipoQueso(
        id = it.id,
        nombre = it.nombre,
        rendimientoEsperadoPct = it.rendimientoEsperadoPct,
        cicloCapital = it.cicloCapital,
        activo = it.activo,
        actualizadoEn = actualizadoEn,
    )
}

internal fun CambiosResponse.aComunicados(actualizadoEn: LocalDateTime): List<Comunicado> = comunicados.map {
    Comunicado(
        id = it.id,
        mensaje = it.mensaje,
        fecha = it.fecha,
        zonasNombres = it.zonasNombres,
        actualizadoEn = actualizadoEn,
    )
}

internal fun CambiosResponse.aPredicciones(actualizadoEn: LocalDateTime): List<PrediccionProveedor> =
    prediccionesProveedor.map {
        PrediccionProveedor(
            proveedorId = it.proveedorId,
            fechaPrevista = it.fechaPrevista,
            litrosEstimadosMin = it.litrosEstimadosMin,
            litrosEstimadosMax = it.litrosEstimadosMax,
            actualizadoEn = actualizadoEn,
        )
    }
