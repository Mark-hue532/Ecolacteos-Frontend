package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.synchronization.EstadoSync
import com.ecolacteos.acopio.synchronization.SyncEngine
import kotlinx.coroutines.flow.StateFlow

/** `S-04`: si el motor está `SINCRONIZANDO` ahora mismo (`§4`: "progreso por recurso, no una barra global"). */
class ObservarEstadoSyncUseCase(private val syncEngine: SyncEngine) {
    operator fun invoke(): StateFlow<EstadoSync> = syncEngine.estado
}
