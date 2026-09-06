package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.data.repository.CatalogoRepository
import com.ecolacteos.acopio.data.repository.LoteProduccionRepository
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.data.repository.VentaRepository
import com.ecolacteos.acopio.domain.GestorSesion

/** Resultado de [LogoutUseCase] -- la UI de Fase 7+ usa [BloqueadaPorPendientes] para mostrar "Tenés N registros sin enviar" (`§4`). */
sealed interface ResultadoLogout {
    data object Cerrada : ResultadoLogout
    data class BloqueadaPorPendientes(val conteo: ConteoPendientes) : ResultadoLogout
}

/**
 * Política de logout de `MOBILE_ARCHITECTURE.md §4`, literal (`PROMPT_FASE_06.md §6`):
 * - 0 pendientes: borra token, las 3 caches con datos personales (RNF-12), y el historial ya `SYNCED` de
 *   esta sesión. Termina.
 * - N > 0 pendientes: **no borra nada**, devuelve [BloqueadaPorPendientes] -- la UI ofrece "sincronizar
 *   ahora" o "cerrar sesión conservando los datos" (`§4`).
 * - Si el caller elige explícitamente la segunda opción ([conservarDatos] = `true`, parámetro separado, no
 *   un segundo llamado ambiguo al mismo método -- `§6` punto 2): borra solo token + caches personales,
 *   **nunca** las tablas `*_local` con pendientes (`CLAUDE.md §3.6`).
 */
class LogoutUseCase(
    private val gestorSesion: GestorSesion,
    private val verificarPendientes: VerificarPendientesUseCase,
    private val registroAcopioRepository: RegistroAcopioRepository,
    private val analisisCalidadRepository: AnalisisCalidadRepository,
    private val loteProduccionRepository: LoteProduccionRepository,
    private val ventaRepository: VentaRepository,
    private val catalogoRepository: CatalogoRepository,
) {
    suspend operator fun invoke(conservarDatos: Boolean = false): ResultadoLogout {
        val conteo = verificarPendientes.contar()
        if (conteo.total > 0 && !conservarDatos) {
            return ResultadoLogout.BloqueadaPorPendientes(conteo)
        }

        if (!conservarDatos) {
            // Acá conteo.total == 0 (si no, ya se retornó arriba): seguro borrar el historial YA
            // confirmado de esta sesión. Las filas no-SYNCED nunca llegan a este punto (CLAUDE.md §3.6).
            registroAcopioRepository.purgarSincronizados()
            analisisCalidadRepository.purgarSincronizados()
            loteProduccionRepository.purgarSincronizados()
            ventaRepository.purgarSincronizados()
        }

        // Se borran siempre, con o sin pendientes, con o sin conservarDatos (§4, RNF-12): son datos
        // personales de proveedores, no trabajo del usuario -- no aplica la protección de §3.6.
        registroAcopioRepository.borrarCacheAjenos()
        catalogoRepository.borrarDatosPersonales()

        // Último paso a propósito: las 4 repository de arriba todavía necesitaban leer el usuarioId de la
        // sesión activa (vía GestorSesion.sesion) para escoparse -- invalidar antes les rompería el filtro.
        gestorSesion.invalidarSesion()

        return ResultadoLogout.Cerrada
    }
}
