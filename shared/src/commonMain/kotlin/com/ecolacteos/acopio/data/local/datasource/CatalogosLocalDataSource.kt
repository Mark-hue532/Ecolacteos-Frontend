package com.ecolacteos.acopio.data.local.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.DispatcherProvider
import com.ecolacteos.acopio.data.local.Comunicado_cache
import com.ecolacteos.acopio.data.local.ComunicadoCacheQueries
import com.ecolacteos.acopio.data.local.ComunicadoZonaCacheQueries
import com.ecolacteos.acopio.data.local.Motivo_observacion_cache
import com.ecolacteos.acopio.data.local.MotivoObservacionCacheQueries
import com.ecolacteos.acopio.data.local.Prediccion_proveedor_cache
import com.ecolacteos.acopio.data.local.PrediccionProveedorCacheQueries
import com.ecolacteos.acopio.data.local.PrecioLitroVigenteCacheQueries
import com.ecolacteos.acopio.data.local.Proveedor_cache
import com.ecolacteos.acopio.data.local.ProveedorCacheQueries
import com.ecolacteos.acopio.data.local.Tipo_queso_cache
import com.ecolacteos.acopio.data.local.TipoQuesoCacheQueries
import com.ecolacteos.acopio.data.local.Unidad_cache
import com.ecolacteos.acopio.data.local.UnidadCacheQueries
import com.ecolacteos.acopio.domain.model.Comunicado
import com.ecolacteos.acopio.domain.model.MotivoObservacion
import com.ecolacteos.acopio.domain.model.PrecioLitroVigente
import com.ecolacteos.acopio.domain.model.PrediccionProveedor
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.model.TipoQueso
import com.ecolacteos.acopio.domain.model.Unidad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime

/**
 * Local Data Source de las 7 tablas de catálogo "simples" que siguen el patrón `DELETE` + `INSERT`
 * (`PROMPT_FASE_04.md §5-6`): `proveedor_cache`, `unidad_cache`, `motivo_observacion_cache`,
 * `tipo_queso_cache`, `comunicado_cache` + `comunicado_zona_cache`, `prediccion_proveedor_cache`,
 * `precio_litro_vigente_cache`. `registro_acopio_cache` y `ruta_zona_cache` son las 2 excepciones (C-04) y
 * viven en sus propias clases ([RegistroAcopioCacheLocalDataSource], [RutaZonaLocalDataSource]).
 *
 * Cada `reemplazarTodo` corre en **una sola transacción SQLDelight** (`database.transaction { ... }`):
 * un fallo a mitad de camino (ej. una fila que viola un `CHECK`) deja la tabla en su estado anterior, no
 * vacía ni parcial.
 */
class CatalogosLocalDataSource(
    private val proveedorQueries: ProveedorCacheQueries,
    private val unidadQueries: UnidadCacheQueries,
    private val motivoObservacionQueries: MotivoObservacionCacheQueries,
    private val tipoQuesoQueries: TipoQuesoCacheQueries,
    private val comunicadoQueries: ComunicadoCacheQueries,
    private val comunicadoZonaQueries: ComunicadoZonaCacheQueries,
    private val prediccionProveedorQueries: PrediccionProveedorCacheQueries,
    private val precioLitroVigenteQueries: PrecioLitroVigenteCacheQueries,
    private val dispatchers: DispatcherProvider,
) {
    fun reemplazarProveedores(filas: List<Proveedor>) {
        proveedorQueries.transaction {
            proveedorQueries.deleteAll()
            filas.forEach { fila ->
                proveedorQueries.insert(
                    id = fila.id,
                    nombre = fila.nombre,
                    zonaActualId = fila.zonaActualId,
                    zonaActualNombre = fila.zonaActualNombre,
                    codigoQr = fila.codigoQr,
                    actualizadoEn = fila.actualizadoEn,
                )
            }
        }
    }

    fun observarProveedores(): Flow<List<Proveedor>> =
        proveedorQueries.selectAll().asFlow().mapToList(dispatchers.io).map { filas -> filas.map { it.aDominio() } }

    /**
     * Fase 8A (`A-02`): agrega/actualiza un único proveedor resuelto por `GET /api/proveedores/qr/{codigoQr}`
     * sin tocar el resto de la tabla -- a diferencia de [reemplazarProveedores] (reemplazo completo de
     * `/sync/cambios`), nunca borra nada.
     */
    fun upsertProveedor(fila: Proveedor) {
        proveedorQueries.upsert(
            id = fila.id,
            nombre = fila.nombre,
            zonaActualId = fila.zonaActualId,
            zonaActualNombre = fila.zonaActualNombre,
            codigoQr = fila.codigoQr,
            actualizadoEn = fila.actualizadoEn,
        )
    }

    /**
     * Logout (Fase 6 §6, C-09 RNF-12): `proveedor_cache` es una de las 3 tablas con datos personales de
     * proveedores que `MOBILE_ARCHITECTURE.md §4` exige borrar siempre al cerrar sesión. Reusa el mismo
     * `deleteAll` que ya existía para el reemplazo de catálogo -- acá no hace falta transacción porque no
     * se inserta nada después.
     */
    fun borrarProveedores() {
        proveedorQueries.deleteAll()
    }

    fun reemplazarUnidades(filas: List<Unidad>) {
        unidadQueries.transaction {
            unidadQueries.deleteAll()
            filas.forEach { fila ->
                unidadQueries.insert(
                    id = fila.id,
                    placa = fila.placa,
                    capacidadTon = fila.capacidadTon,
                    zonaId = fila.zonaId,
                    responsableId = fila.responsableId,
                    responsableNombre = fila.responsableNombre,
                    actualizadoEn = fila.actualizadoEn,
                )
            }
        }
    }

    fun observarUnidades(): Flow<List<Unidad>> =
        unidadQueries.selectAll().asFlow().mapToList(dispatchers.io).map { filas -> filas.map { it.aDominio() } }

    fun reemplazarMotivosObservacion(filas: List<MotivoObservacion>) {
        motivoObservacionQueries.transaction {
            motivoObservacionQueries.deleteAll()
            filas.forEach { fila ->
                motivoObservacionQueries.insert(
                    id = fila.id,
                    descripcion = fila.descripcion,
                    actualizadoEn = fila.actualizadoEn,
                )
            }
        }
    }

    fun observarMotivosObservacion(): Flow<List<MotivoObservacion>> =
        motivoObservacionQueries.selectAll().asFlow().mapToList(dispatchers.io)
            .map { filas -> filas.map { it.aDominio() } }

    fun reemplazarTiposQueso(filas: List<TipoQueso>) {
        tipoQuesoQueries.transaction {
            tipoQuesoQueries.deleteAll()
            filas.forEach { fila ->
                tipoQuesoQueries.insert(
                    id = fila.id,
                    nombre = fila.nombre,
                    rendimientoEsperadoPct = fila.rendimientoEsperadoPct,
                    cicloCapital = fila.cicloCapital,
                    activo = fila.activo,
                    actualizadoEn = fila.actualizadoEn,
                )
            }
        }
    }

    fun observarTiposQueso(): Flow<List<TipoQueso>> =
        tipoQuesoQueries.selectAll().asFlow().mapToList(dispatchers.io).map { filas -> filas.map { it.aDominio() } }

    /** Reemplaza `comunicado_cache` **y** `comunicado_zona_cache` juntos, en la misma transacción. */
    fun reemplazarComunicados(filas: List<Comunicado>) {
        comunicadoQueries.transaction {
            comunicadoQueries.deleteAll()
            comunicadoZonaQueries.deleteAll()
            filas.forEach { fila ->
                comunicadoQueries.insert(
                    id = fila.id,
                    mensaje = fila.mensaje,
                    fecha = fila.fecha,
                    actualizadoEn = fila.actualizadoEn,
                )
                fila.zonasNombres.forEach { zonaNombre ->
                    comunicadoZonaQueries.insert(comunicadoId = fila.id, zonaNombre = zonaNombre)
                }
            }
        }
    }

    /**
     * No usa `.asFlow()` directo de `comunicadoQueries` porque la fila de dominio necesita componer
     * [Comunicado.zonasNombres] desde `comunicado_zona_cache` -- se recompone en cada emisión, aceptable
     * para un catálogo chico. Reactivo a cambios de `comunicado_cache` (donde siempre se escribe junto con
     * `comunicado_zona_cache`, ver [reemplazarComunicados]), no a un cambio aislado de la tabla hija -- ese
     * caso no ocurre en este esquema.
     */
    fun observarComunicados(): Flow<List<Comunicado>> =
        comunicadoQueries.selectAll().asFlow().mapToList(dispatchers.io)
            .map { filas -> filas.map { it.aDominio(obtenerZonas(it.id)) } }

    private fun obtenerZonas(comunicadoId: String): List<String> =
        comunicadoZonaQueries.obtenerPorComunicado(comunicadoId).executeAsList()

    fun reemplazarPredicciones(filas: List<PrediccionProveedor>) {
        prediccionProveedorQueries.transaction {
            prediccionProveedorQueries.deleteAll()
            filas.forEach { fila ->
                prediccionProveedorQueries.insert(
                    proveedorId = fila.proveedorId,
                    fechaPrevista = fila.fechaPrevista,
                    litrosEstimadosMin = fila.litrosEstimadosMin,
                    litrosEstimadosMax = fila.litrosEstimadosMax,
                    actualizadoEn = fila.actualizadoEn,
                )
            }
        }
    }

    fun observarPredicciones(): Flow<List<PrediccionProveedor>> =
        prediccionProveedorQueries.selectAll().asFlow().mapToList(dispatchers.io)
            .map { filas -> filas.map { it.aDominio() } }

    /**
     * A diferencia de [obtenerPrecioVigente] (que colapsa "sin fila"/"precio NULL" en un solo `null`), acá
     * [precio] sí viaja nullable tal cual lo entrega `CambiosResponse.precioLitroVigente` (`DATA`:
     * `.orElse(null)`) -- el llamador (Fase 5+) siempre conoce el momento del sync, así que [actualizadoEn]
     * no es opcional: la fila se escribe siempre, con o sin precio configurado.
     */
    fun reemplazarPrecioLitroVigente(precio: Decimal?, actualizadoEn: LocalDateTime) {
        precioLitroVigenteQueries.transaction {
            precioLitroVigenteQueries.deleteAll()
            precioLitroVigenteQueries.insert(precio = precio, actualizadoEn = actualizadoEn)
        }
    }

    /**
     * "No hay fila" (nunca se sincronizó) y "hay fila con `precio = NULL`" (el backend no tiene precio
     * vigente) se colapsan en `null` -- decisión documentada en [PrecioLitroVigente].
     */
    fun obtenerPrecioVigente(): PrecioLitroVigente? =
        precioLitroVigenteQueries.obtener().executeAsOneOrNull()
            ?.takeIf { it.precio != null }
            ?.let { PrecioLitroVigente(precio = it.precio!!, actualizadoEn = it.actualizado_en) }
}

private fun Proveedor_cache.aDominio(): Proveedor = Proveedor(
    id = id,
    nombre = nombre,
    zonaActualId = zona_actual_id,
    zonaActualNombre = zona_actual_nombre,
    codigoQr = codigo_qr,
    actualizadoEn = actualizado_en,
)

private fun Unidad_cache.aDominio(): Unidad = Unidad(
    id = id,
    placa = placa,
    capacidadTon = capacidad_ton,
    zonaId = zona_id,
    responsableId = responsable_id,
    responsableNombre = responsable_nombre,
    actualizadoEn = actualizado_en,
)

private fun Motivo_observacion_cache.aDominio(): MotivoObservacion = MotivoObservacion(
    id = id,
    descripcion = descripcion,
    actualizadoEn = actualizado_en,
)

private fun Tipo_queso_cache.aDominio(): TipoQueso = TipoQueso(
    id = id,
    nombre = nombre,
    rendimientoEsperadoPct = rendimiento_esperado_pct,
    cicloCapital = ciclo_capital,
    activo = activo,
    actualizadoEn = actualizado_en,
)

private fun Comunicado_cache.aDominio(zonasNombres: List<String>): Comunicado = Comunicado(
    id = id,
    mensaje = mensaje,
    fecha = fecha,
    zonasNombres = zonasNombres,
    actualizadoEn = actualizado_en,
)

private fun Prediccion_proveedor_cache.aDominio(): PrediccionProveedor = PrediccionProveedor(
    proveedorId = proveedor_id,
    fechaPrevista = fecha_prevista,
    litrosEstimadosMin = litros_estimados_min,
    litrosEstimadosMax = litros_estimados_max,
    actualizadoEn = actualizado_en,
)
