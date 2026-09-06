package com.ecolacteos.acopio.synchronization

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * `ConnectivityManager.registerNetworkCallback` sobre una `NetworkRequest` que exige
 * `NET_CAPABILITY_INTERNET` **y** `NET_CAPABILITY_VALIDATED` (`MOBILE_ARCHITECTURE.md §6.5`): "hay wifi" no
 * alcanza -- un portal cautivo de un grifo o un router sin salida son exactamente el caso que rompe el
 * sync en campo. Aun así la señal sigue siendo "intentar", no una garantía.
 *
 * `context` por constructor, lo arma el módulo de Koin de `androidApp` (mismo criterio que
 * `SecureTokenStorage.android.kt`). `applicationContext`, no la Activity: el callback vive mientras alguien
 * colecte el `Flow`, no mientras haya una pantalla.
 */
actual class ConnectivityObserverDePlataforma(context: Context) : ConnectivityObserver {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    actual override val conectado: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(hayRedValidada())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }
        }

        // Valor inicial: quien se suscribe necesita saber el estado actual, no esperar a la próxima transición.
        trySend(hayRedValidada())

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun hayRedValidada(): Boolean {
        val red = connectivityManager.activeNetwork ?: return false
        val capacidades = connectivityManager.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
