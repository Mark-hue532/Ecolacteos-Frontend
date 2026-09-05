package com.ecolacteos.acopio.core

import kotlinx.coroutines.Dispatchers

// Kotlin/Native no tiene `Dispatchers.IO` (es JVM-only) -- se usa `Default` también para trabajo de E/S,
// igual que recomienda la documentación de kotlinx.coroutines para Native.
actual fun dispatcherProviderDeSistema(): DispatcherProvider = object : DispatcherProvider {
    override val default = Dispatchers.Default
    override val io = Dispatchers.Default
    override val main = Dispatchers.Main
}
