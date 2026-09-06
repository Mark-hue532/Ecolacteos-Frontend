package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.synchronization.ConnectivityObserver
import com.ecolacteos.acopio.synchronization.SyncEngine
import com.ecolacteos.acopio.synchronization.SyncEngineImpl
import org.koin.dsl.module

/**
 * Wiring real de la Fase 5 (`PROMPT_FASE_06.md §2`) -- nadie conectaba `SyncEngine` a Koin todavía.
 *
 * Tampoco declara el binding de [ConnectivityObserver] -- mismo motivo que [com.ecolacteos.acopio.data.local.AcopioDriverFactory]
 * en `di/LocalModule.kt`: `ConnectivityObserverDePlataforma` necesita `Context` en Android, nada en iOS.
 *
 * `SyncEngine.observarConectividad(scope)` **no** se dispara acá: Koin arma el grafo, no decide cuándo
 * empieza a correr una corrutina de larga duración -- eso depende de un `CoroutineScope` con el ciclo de
 * vida de la app real, que todavía no existe (Fase 7 monta la primera pantalla). Hasta entonces, el motor
 * solo se dispara vía `solicitarSyncOportunista()` (después de cada `crear()`, ya wireado en
 * `di/RepositoryModule.kt`) o llamando `ejecutarCiclo()`/`observarConectividad()` a mano en tests.
 */
val syncModule = module {
    single<SyncEngine> {
        SyncEngineImpl(
            apiClient = get(),
            gestorSesion = get(),
            registrosLocal = get(),
            analisisLocal = get(),
            lotesLocal = get(),
            ventasLocal = get(),
            catalogosLocal = get(),
            conectividad = get<ConnectivityObserver>(),
            dispatchers = get(),
        )
    }
}
