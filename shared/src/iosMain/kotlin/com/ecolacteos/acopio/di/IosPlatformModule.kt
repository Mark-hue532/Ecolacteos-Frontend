package com.ecolacteos.acopio.di

import app.cash.sqldelight.db.SqlDriver
import com.ecolacteos.acopio.data.local.AcopioDriverFactory
import com.ecolacteos.acopio.synchronization.ConnectivityObserver
import com.ecolacteos.acopio.synchronization.ConnectivityObserverDePlataforma
import org.koin.dsl.module

/**
 * Bindings de iOS para `SqlDriver` (`data/local/AcopioDriverFactory.kt`, Fase 4) y `ConnectivityObserver`
 * (`synchronization/ConnectivityObserver.kt`, Fase 5) -- mismo motivo que [moduloSeguridadIos]: los dos
 * `expect class` no declaran constructor común (Android necesita `Context`, iOS no necesita nada), así
 * que cada plataforma los registra en su propio módulo. `iOSApp.swift` (todavía no existe, ver
 * `iosApp/README.md`) solo tiene que llamar a esta función junto con [moduloSeguridadIos] y pasar ambas a
 * `initKoin`.
 */
fun moduloPlataformaIos() = module {
    single<SqlDriver> { AcopioDriverFactory().crearDriver() }
    single<ConnectivityObserver> { ConnectivityObserverDePlataforma() }
}
