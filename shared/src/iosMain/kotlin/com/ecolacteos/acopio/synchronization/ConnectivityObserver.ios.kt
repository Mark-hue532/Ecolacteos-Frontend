package com.ecolacteos.acopio.synchronization

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * `NWPathMonitor` (`MOBILE_ARCHITECTURE.md §6.5`), vía la API C de `Network.framework` que es la que expone
 * Kotlin/Native. `nw_path_status_satisfied` es el equivalente de "hay una ruta utilizable" -- misma
 * advertencia que en Android: es señal de "intentar", no garantía de Internet.
 *
 * Sin constructor: iOS no necesita `Context` ni nada equivalente (mismo contraste que en Fase 3 y 4).
 *
 * ⚠️ Sin verificar en dispositivo/simulador desde esta sesión (Windows, `CLAUDE.md §8`) -- compila, pero
 * el comportamiento real se confirma en `verificacion-ios.yml`, igual que el driver de SQLDelight.
 */
@OptIn(ExperimentalForeignApi::class)
actual class ConnectivityObserverDePlataforma : ConnectivityObserver {

    actual override val conectado: Flow<Boolean> = callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            trySend(nw_path_get_status(path) == nw_path_status_satisfied)
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)

        awaitClose { nw_path_monitor_cancel(monitor) }
    }.distinctUntilChanged()
}
