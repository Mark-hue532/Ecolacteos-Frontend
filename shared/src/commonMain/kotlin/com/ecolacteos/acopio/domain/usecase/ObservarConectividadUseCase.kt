package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.synchronization.ConnectivityObserver
import kotlinx.coroutines.flow.Flow

/**
 * `hayConexion` de casi todos los `UiState` de esta fase (`S-03`, `S-04`, `V-01`, `V-02`). `ViewModel`
 * nunca inyecta `ConnectivityObserver` directo -- no es un `Repository` ni un `UseCase`, pero es una señal
 * de la capa de sincronización (`CLAUDE.md §3.4`), y este wrapper de una línea evita que la regla de capas
 * tenga una excepción tácita para "señales", que sería el mismo argumento que se usaría después para
 * cualquier otra cosa.
 */
class ObservarConectividadUseCase(private val connectivityObserver: ConnectivityObserver) {
    operator fun invoke(): Flow<Boolean> = connectivityObserver.conectado
}
