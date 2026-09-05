package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.core.DispatcherProvider
import com.ecolacteos.acopio.core.dispatcherProviderDeSistema
import org.koin.dsl.module

/**
 * Módulo raíz de esta fase: dispatchers y utilidades sin dependencias de plataforma más allá del propio
 * `expect`/`actual`. Cada fase siguiente agrega su propio módulo (`networkModule`, `localModule`,
 * `syncModule`, ...) y los suma en [initKoin].
 */
val coreModule = module {
    single<DispatcherProvider> { dispatcherProviderDeSistema() }
}
