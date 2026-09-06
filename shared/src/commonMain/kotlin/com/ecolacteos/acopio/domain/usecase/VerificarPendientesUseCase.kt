package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.data.repository.LoteProduccionRepository
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.data.repository.VentaRepository
import com.ecolacteos.acopio.domain.VerificadorPendientes
import com.ecolacteos.acopio.domain.model.SyncStatus
import kotlinx.coroutines.flow.first

/** Cuántas filas sin sincronizar hay en cada uno de los 4 recursos, para la sesión activa (`§6`). */
data class ConteoPendientes(
    val registros: Int,
    val analisis: Int,
    val lotes: Int,
    val ventas: Int,
) {
    val total: Int get() = registros + analisis + lotes + ventas
}

/**
 * Reemplaza el stub de Fase 3 (`VerificadorPendientesSinImplementar`) con una implementación real contra
 * las 4 tablas `*_local` (`PROMPT_FASE_06.md §6`), filtrada por `usuario_id` = sesión activa (C-09
 * multiusuario -- trampa #5).
 *
 * Implementa [VerificadorPendientes] (interfaz de Fase 3, sin cambios) **además** de su propia API con
 * conteo: `GestorSesionImpl.cerrarSesion()` (Fase 3) solo necesita el booleano, `LogoutUseCase` (Fase 6)
 * necesita el número real para "Tenés N registros sin enviar" (`§4` Política de logout).
 *
 * No consulta `data/local/datasource/` directo -- reusa `observarPendientes()` de los 4 `Repository`, que
 * ya está escopeado a la sesión activa vía `GestorSesion.sesion` (`flatMapLatest`, ver
 * `RegistroAcopioRepositoryImpl` et al.). El filtro `!= SYNCED` es el mismo criterio que `CLAUDE.md §3.6`.
 */
class VerificarPendientesUseCase(
    private val registroAcopioRepository: RegistroAcopioRepository,
    private val analisisCalidadRepository: AnalisisCalidadRepository,
    private val loteProduccionRepository: LoteProduccionRepository,
    private val ventaRepository: VentaRepository,
) : VerificadorPendientes {

    suspend fun contar(): ConteoPendientes = ConteoPendientes(
        registros = registroAcopioRepository.observarPendientes().first().count { it.syncStatus != SyncStatus.SYNCED },
        analisis = analisisCalidadRepository.observarPendientes().first().count { it.syncStatus != SyncStatus.SYNCED },
        lotes = loteProduccionRepository.observarPendientes().first().count { it.syncStatus != SyncStatus.SYNCED },
        ventas = ventaRepository.observarPendientes().first().count { it.syncStatus != SyncStatus.SYNCED },
    )

    override suspend fun hayTrabajoSinSincronizar(): Boolean = contar().total > 0
}
