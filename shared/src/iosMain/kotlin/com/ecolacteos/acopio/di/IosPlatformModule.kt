package com.ecolacteos.acopio.di

import app.cash.sqldelight.db.SqlDriver
import com.ecolacteos.acopio.data.local.AcopioDriverFactory
import com.ecolacteos.acopio.plataforma.GestorPermisos
import com.ecolacteos.acopio.plataforma.GestorPermisosDePlataforma
import com.ecolacteos.acopio.plataforma.ProveedorUbicacion
import com.ecolacteos.acopio.plataforma.ProveedorUbicacionDePlataforma
import com.ecolacteos.acopio.synchronization.ConnectivityObserver
import com.ecolacteos.acopio.synchronization.ConnectivityObserverDePlataforma
import org.koin.dsl.module

/**
 * Bindings de iOS para `SqlDriver` (`data/local/AcopioDriverFactory.kt`, Fase 4), `ConnectivityObserver`
 * (`synchronization/ConnectivityObserver.kt`, Fase 5) y, desde la Fase 8A, `GestorPermisos`/
 * `ProveedorUbicacion` (`plataforma/`) -- mismo motivo que [moduloSeguridadIos]: estos `expect class` no
 * declaran constructor común (Android necesita `Context`, iOS no necesita nada), así que cada plataforma
 * los registra en su propio módulo. `iOSApp.swift` (todavía no existe, ver `iosApp/README.md`) solo tiene
 * que llamar a esta función junto con [moduloSeguridadIos] y pasar ambas a `initKoin`.
 */
fun moduloPlataformaIos() = module {
    single<SqlDriver> { AcopioDriverFactory().crearDriver() }
    single<ConnectivityObserver> { ConnectivityObserverDePlataforma() }
    single<GestorPermisos> { GestorPermisosDePlataforma() }
    single<ProveedorUbicacion> { ProveedorUbicacionDePlataforma() }
}
