package com.ecolacteos.acopio.core

import kotlinx.coroutines.Dispatchers

// Target `jvm()` -- existe solo para correr commonTest rápido sin emulador/simulador
// (MOBILE_ARCHITECTURE.md §14, "Testing"). No es un target de producción.
actual fun dispatcherProviderDeSistema(): DispatcherProvider = object : DispatcherProvider {
    override val default = Dispatchers.Default
    override val io = Dispatchers.IO
    override val main = Dispatchers.Default // no hay UI dispatcher en JVM puro; no se usa fuera de tests
}
