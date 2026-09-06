package com.ecolacteos.acopio.data.local

import app.cash.sqldelight.db.SqlDriver
import com.ecolacteos.acopio.data.local.adapter.BigDecimalEscala2ColumnAdapter
import com.ecolacteos.acopio.data.local.adapter.BigDecimalEscala6ColumnAdapter
import com.ecolacteos.acopio.data.local.adapter.CicloCapitalColumnAdapter
import com.ecolacteos.acopio.data.local.adapter.IntColumnAdapter
import com.ecolacteos.acopio.data.local.adapter.LocalDateColumnAdapter
import com.ecolacteos.acopio.data.local.adapter.LocalDateTimeColumnAdapter
import com.ecolacteos.acopio.data.local.adapter.LocalTimeColumnAdapter
import com.ecolacteos.acopio.data.local.adapter.OrigenColumnAdapter
import com.ecolacteos.acopio.data.local.adapter.SyncStatusColumnAdapter
import com.ecolacteos.acopio.data.local.adapter.TipoClienteVentaColumnAdapter

/**
 * Único lugar que arma [AcopioDatabase] con los `ColumnAdapter` de las 15 tablas (`PROMPT_FASE_04.md §3`) --
 * ni `commonTest` ni un futuro Repository (Fase 6) deberían repetir esta lista a mano. `lote_produccion_
 * registro_local` y `comunicado_zona_cache` no aparecen acá: no tienen columnas adaptadas (todo `TEXT`
 * plano), SQLDelight no genera un parámetro `Adapter` para ellas.
 *
 * Los nombres de parámetro de cada `Adapter` generado preservan el `snake_case` de la columna SQL tal cual
 * (`fecha_horaAdapter`, no `fechaHoraAdapter`) -- SQLDelight no convierte a camelCase los identificadores
 * que genera a partir del nombre de columna, solo los nombres de parámetro con bind (`:nombre`) que uno
 * elige en el `.sq`. Confirmado compilando, no asumido (ver checkpoint).
 */
fun crearAcopioDatabase(driver: SqlDriver): AcopioDatabase = AcopioDatabase(
    driver = driver,
    registro_acopio_localAdapter = Registro_acopio_local.Adapter(
        fecha_horaAdapter = LocalDateTimeColumnAdapter,
        litrosAdapter = BigDecimalEscala2ColumnAdapter,
        gps_latAdapter = BigDecimalEscala6ColumnAdapter,
        gps_lngAdapter = BigDecimalEscala6ColumnAdapter,
        sync_statusAdapter = SyncStatusColumnAdapter,
        sync_attemptsAdapter = IntColumnAdapter,
        next_attempt_atAdapter = LocalDateTimeColumnAdapter,
        creado_enAdapter = LocalDateTimeColumnAdapter,
        sincronizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    analisis_calidad_localAdapter = Analisis_calidad_local.Adapter(
        aguaAdapter = BigDecimalEscala2ColumnAdapter,
        proteinaAdapter = BigDecimalEscala2ColumnAdapter,
        lactosaAdapter = BigDecimalEscala2ColumnAdapter,
        densidadAdapter = BigDecimalEscala2ColumnAdapter,
        temperaturaAdapter = BigDecimalEscala2ColumnAdapter,
        phAdapter = BigDecimalEscala2ColumnAdapter,
        sync_statusAdapter = SyncStatusColumnAdapter,
        sync_attemptsAdapter = IntColumnAdapter,
        next_attempt_atAdapter = LocalDateTimeColumnAdapter,
        creado_enAdapter = LocalDateTimeColumnAdapter,
        sincronizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    lote_produccion_localAdapter = Lote_produccion_local.Adapter(
        fechaAdapter = LocalDateColumnAdapter,
        litros_usadosAdapter = BigDecimalEscala2ColumnAdapter,
        unidades_obtenidasAdapter = IntColumnAdapter,
        sync_statusAdapter = SyncStatusColumnAdapter,
        sync_attemptsAdapter = IntColumnAdapter,
        next_attempt_atAdapter = LocalDateTimeColumnAdapter,
        creado_enAdapter = LocalDateTimeColumnAdapter,
        sincronizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    venta_localAdapter = Venta_local.Adapter(
        fechaAdapter = LocalDateColumnAdapter,
        tipo_clienteAdapter = TipoClienteVentaColumnAdapter,
        cantidadAdapter = IntColumnAdapter,
        precio_unitarioAdapter = BigDecimalEscala2ColumnAdapter,
        sync_statusAdapter = SyncStatusColumnAdapter,
        sync_attemptsAdapter = IntColumnAdapter,
        next_attempt_atAdapter = LocalDateTimeColumnAdapter,
        creado_enAdapter = LocalDateTimeColumnAdapter,
        sincronizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    proveedor_cacheAdapter = Proveedor_cache.Adapter(actualizado_enAdapter = LocalDateTimeColumnAdapter),
    unidad_cacheAdapter = Unidad_cache.Adapter(
        capacidad_tonAdapter = BigDecimalEscala2ColumnAdapter,
        actualizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    motivo_observacion_cacheAdapter = Motivo_observacion_cache.Adapter(
        actualizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    tipo_queso_cacheAdapter = Tipo_queso_cache.Adapter(
        rendimiento_esperado_pctAdapter = BigDecimalEscala2ColumnAdapter,
        ciclo_capitalAdapter = CicloCapitalColumnAdapter,
        actualizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    comunicado_cacheAdapter = Comunicado_cache.Adapter(
        fechaAdapter = LocalDateTimeColumnAdapter,
        actualizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    prediccion_proveedor_cacheAdapter = Prediccion_proveedor_cache.Adapter(
        fecha_previstaAdapter = LocalDateColumnAdapter,
        litros_estimados_minAdapter = BigDecimalEscala2ColumnAdapter,
        litros_estimados_maxAdapter = BigDecimalEscala2ColumnAdapter,
        actualizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    precio_litro_vigente_cacheAdapter = Precio_litro_vigente_cache.Adapter(
        precioAdapter = BigDecimalEscala2ColumnAdapter,
        actualizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    ruta_zona_cacheAdapter = Ruta_zona_cache.Adapter(
        ordenAdapter = IntColumnAdapter,
        hora_estimadaAdapter = LocalTimeColumnAdapter,
        actualizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    registro_acopio_cacheAdapter = Registro_acopio_cache.Adapter(
        fecha_horaAdapter = LocalDateTimeColumnAdapter,
        litrosAdapter = BigDecimalEscala2ColumnAdapter,
        origenAdapter = OrigenColumnAdapter,
        actualizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
    // Fase 7 (`PROMPT_FASE_07.md §2.5`) -- ver `BorradorFormulario.sq`.
    borrador_formularioAdapter = Borrador_formulario.Adapter(
        actualizado_enAdapter = LocalDateTimeColumnAdapter,
    ),
)
