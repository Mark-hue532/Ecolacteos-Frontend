package com.ecolacteos.acopio.core

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Fuente única de dispatchers de coroutines para todo `shared/`. Nada de lógica acopla
 * `Dispatchers.Main`/`Dispatchers.IO` directamente -- todo recibe un [DispatcherProvider] (por Koin), así
 * los tests pueden inyectar uno propio con `UnconfinedTestDispatcher` en las tres propiedades.
 *
 * `io` existe como propiedad separada de `default` porque `Dispatchers.IO` **no existe en `commonMain`**
 * de `kotlinx-coroutines-core` (es JVM-only) -- cada plataforma resuelve la suya en su `actual`.
 */
interface DispatcherProvider {
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
}

/** Implementación real por plataforma -- Android/JVM usa `Dispatchers.IO`, iOS no lo tiene y usa `Default`. */
expect fun dispatcherProviderDeSistema(): DispatcherProvider
