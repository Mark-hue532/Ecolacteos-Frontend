package com.ecolacteos.acopio.synchronization

import kotlinx.coroutines.flow.Flow

/**
 * Señal de conectividad que observa el [SyncEngine] (`MOBILE_ARCHITECTURE.md §6.5`). Es una señal de
 * **"intentar"**, no de "hay Internet garantizado": el propio intento de red (timeout/error) sigue siendo
 * la fuente de verdad final, por eso el motor nunca decide "no puedo sincronizar" solo porque esto emita
 * `false` -- lo usa para *disparar* un ciclo, no para vetarlo.
 *
 * Es una `interface` (no un `expect` a secas) para que `commonTest` pueda inyectar un fake sin mockear
 * nada de plataforma; la implementación real por plataforma es [ConnectivityObserverDePlataforma].
 */
interface ConnectivityObserver {
    /** `true` mientras haya una red con Internet validado. Emite el valor actual al suscribirse. */
    val conectado: Flow<Boolean>
}

/**
 * Implementación nativa por plataforma: Android `ConnectivityManager.registerNetworkCallback` sobre una
 * `NetworkRequest` con `NET_CAPABILITY_INTERNET` **validado**, iOS `NWPathMonitor` (§6.5).
 *
 * Sin constructor común a propósito -- Android necesita un `Context` y iOS no necesita nada, mismo patrón
 * que `security/SecureTokenStorage.kt` (Fase 3) y `data/local/AcopioDriverFactory.kt` (Fase 4):
 * `commonMain` nunca instancia esto, lo arma el módulo de Koin de cada plataforma cuando exista un
 * consumidor real (Fase 6). El target `jvm()` tiene un `actual` mínimo solo para que `:shared:jvmTest`
 * compile; los tests de esta fase usan un fake propio, no esta clase.
 */
expect class ConnectivityObserverDePlataforma : ConnectivityObserver {
    override val conectado: Flow<Boolean>
}
