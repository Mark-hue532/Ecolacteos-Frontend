package com.ecolacteos.acopio.di

import app.cash.sqldelight.db.SqlDriver
import com.ecolacteos.acopio.data.local.AcopioDatabase
import com.ecolacteos.acopio.data.local.crearAcopioDatabase
import com.ecolacteos.acopio.data.local.datasource.AnalisisCalidadLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.CatalogosLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.LoteProduccionLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RegistroAcopioCacheLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RegistroAcopioLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.RutaZonaLocalDataSource
import com.ecolacteos.acopio.data.local.datasource.VentaLocalDataSource
import org.koin.dsl.module

/**
 * Wiring real de la Fase 4 (`PROMPT_FASE_06.md §2`) -- hasta esta fase, nadie conectaba
 * `AcopioDriverFactory`/`AcopioDatabase`/los 7 `LocalDataSource` a Koin.
 *
 * A propósito **no** declara un binding de `SqlDriver` -- mismo motivo que `AlmacenamientoSeguroDeSesion`
 * (`di/SecurityModule.kt`, Fase 3): `AcopioDriverFactory` es un `expect class` sin constructor común
 * (Android necesita `Context`, iOS no necesita nada, ver `data/local/AcopioDriverFactory.kt`), así que
 * cada plataforma registra el `SqlDriver` real en su propio módulo (`androidApp/MainActivity.kt`,
 * `di/IosPlatformModule.kt`). Este módulo solo arma `AcopioDatabase` a partir de ese `SqlDriver` ya
 * resuelto, y expone los 7 `LocalDataSource` sobre esa instancia.
 */
val localModule = module {
    single { crearAcopioDatabase(get<SqlDriver>()) }

    single { RegistroAcopioLocalDataSource(get<AcopioDatabase>().registroAcopioLocalQueries, get()) }
    single { AnalisisCalidadLocalDataSource(get<AcopioDatabase>().analisisCalidadLocalQueries, get()) }
    single {
        val db = get<AcopioDatabase>()
        LoteProduccionLocalDataSource(db.loteProduccionLocalQueries, db.loteProduccionRegistroLocalQueries, get())
    }
    single { VentaLocalDataSource(get<AcopioDatabase>().ventaLocalQueries, get()) }
    single { RegistroAcopioCacheLocalDataSource(get<AcopioDatabase>().registroAcopioCacheQueries, get()) }
    single { RutaZonaLocalDataSource(get<AcopioDatabase>().rutaZonaCacheQueries) }
    single {
        val db = get<AcopioDatabase>()
        CatalogosLocalDataSource(
            proveedorQueries = db.proveedorCacheQueries,
            unidadQueries = db.unidadCacheQueries,
            motivoObservacionQueries = db.motivoObservacionCacheQueries,
            tipoQuesoQueries = db.tipoQuesoCacheQueries,
            comunicadoQueries = db.comunicadoCacheQueries,
            comunicadoZonaQueries = db.comunicadoZonaCacheQueries,
            prediccionProveedorQueries = db.prediccionProveedorCacheQueries,
            precioLitroVigenteQueries = db.precioLitroVigenteCacheQueries,
            dispatchers = get(),
        )
    }
}
