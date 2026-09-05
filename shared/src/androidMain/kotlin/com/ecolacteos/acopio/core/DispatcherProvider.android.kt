package com.ecolacteos.acopio.core

import kotlinx.coroutines.Dispatchers

actual fun dispatcherProviderDeSistema(): DispatcherProvider = object : DispatcherProvider {
    override val default = Dispatchers.Default
    override val io = Dispatchers.IO
    override val main = Dispatchers.Main
}
