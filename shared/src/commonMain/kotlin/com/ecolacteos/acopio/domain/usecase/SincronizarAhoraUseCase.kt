package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.synchronization.ResultadoCiclo
import com.ecolacteos.acopio.synchronization.SyncEngine

/**
 * `S-04`, evento `SincronizarAhoraPresionado` (`MOBILE_SCREENS.md §4`, `PROMPT_FASE_07.md §2.4`): un ciclo
 * forzado (`MOBILE_ARCHITECTURE.md §6.5`), expuesto como `UseCase` -- la UI nunca llama a `SyncEngine`
 * directo (`CLAUDE.md §3.4`).
 */
class SincronizarAhoraUseCase(private val syncEngine: SyncEngine) {
    suspend operator fun invoke(): ResultadoCiclo = syncEngine.ejecutarCiclo()
}
