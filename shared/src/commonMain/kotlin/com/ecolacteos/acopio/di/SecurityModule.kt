package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.GestorSesionImpl
import com.ecolacteos.acopio.network.SesionInvalidadaNotifier
import com.ecolacteos.acopio.network.TokenProvider
import com.ecolacteos.acopio.network.TokenProviderSobreGestorSesion
import org.koin.dsl.module

/**
 * Módulo de sesión de la Fase 3 (`PROMPT_FASE_03.md §5-7`).
 *
 * A propósito **no** declara un binding de `AlmacenamientoSeguroDeSesion`/`SecureTokenStorage` -- ese
 * `expect class` no tiene un constructor común (Android necesita `Context`, iOS no necesita nada, ver
 * `security/SecureTokenStorage.kt`), así que cada plataforma lo registra en su propio módulo de Koin
 * (`androidApp/MainActivity.kt` hoy; futuro `iosApp`) y lo pasa a `initKoin(appDeclaration = ...)`. Mientras
 * ese binding no exista, `GestorSesion` (y todo lo que dependa de él) no se puede resolver -- `CoreModuleTest`
 * lo verifica con un fake apto para JVM, no con el binding de producción.
 *
 * [GestorSesionImpl] se registra en Koin en vez de crearse directo porque necesita registrarse a sí mismo
 * en [SesionInvalidadaNotifier] una sola vez, ya con el grafo armado -- ver el comentario de
 * `SesionInvalidadaNotifier.kt` sobre por qué esto no es una dependencia directa en el constructor.
 *
 * El binding de `VerificadorPendientes` que este módulo consume (`verificadorPendientes = lazy { get() }`
 * abajo) ya no se declara acá -- Fase 6 reemplazó el stub (`VerificadorPendientesSinImplementar`) por
 * `VerificarPendientesUseCase`, registrado en `di/UseCaseModule.kt`. Koin resuelve entre módulos sin
 * problema (no son namespaces aislados), así que el orden de declaración en `initKoin` no importa.
 */
val securityModule = module {
    single<GestorSesion> {
        GestorSesionImpl(
            apiClient = get(),
            almacenamiento = get(),
            // Lazy a propósito -- rompe un ciclo de construcción (VerificarPendientesUseCase necesita los
            // 4 Repository, que a su vez necesitan este mismo GestorSesion). Ver el comentario en
            // GestorSesionImpl.kt y en TokenProviderSobreGestorSesion.kt (mismo patrón, mismo motivo).
            verificadorPendientes = lazy { get() },
        ).also { gestor -> get<SesionInvalidadaNotifier>().registrar { gestor.invalidarSesion() } }
    }
    // gestorSesion es Lazy a proposito -- rompe un ciclo de construccion en el grafo, ver el comentario en
    // TokenProviderSobreGestorSesion.kt.
    single<TokenProvider> { TokenProviderSobreGestorSesion(gestorSesion = lazy { get() }) }
}
