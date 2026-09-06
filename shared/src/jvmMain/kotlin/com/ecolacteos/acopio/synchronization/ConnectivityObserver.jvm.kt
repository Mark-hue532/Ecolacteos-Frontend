package com.ecolacteos.acopio.synchronization

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Target `jvm()` -- existe solo para que `:shared:jvmTest` compile sin emulador/simulador
 * (`MOBILE_ARCHITECTURE.md §14`, "Testing"). Nunca se empaqueta en producción: Android e iOS tienen sus
 * `actual` reales sobre `ConnectivityManager`/`NWPathMonitor`. Emite `true` una sola vez y nada más --
 * los tests de esta fase usan su propio fake con un `MutableStateFlow`, no esta clase.
 */
actual class ConnectivityObserverDePlataforma : ConnectivityObserver {
    actual override val conectado: Flow<Boolean> = flowOf(true)
}
