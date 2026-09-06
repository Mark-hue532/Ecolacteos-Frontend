package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.CatalogoRepository
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDateTime

/**
 * `HomeUiState.resumenSync` (`MOBILE_SCREENS.md §4`, `S-03`): "pendientes, conDependencia, conError,
 * ultimoSyncOk". Versión totalizada de [ResumenPendientes] -- `S-03` no necesita el desglose por recurso
 * (eso es `S-04`, que consume [ObservarPendientesUseCase] directo).
 *
 * [ultimoSyncOk] sale de `TipoQueso.actualizadoEn` (`tipo_queso_cache`): es la hora de pared **del
 * dispositivo** en el momento en que se procesó el último `/sync/cambios` exitoso
 * (`SyncMappers.aTiposQueso`, Fase 5) -- no una columna de auditoría del servidor, así que usarla acá no
 * contradice `§10.3`. `null` si el catálogo nunca se sincronizó (primer arranque).
 */
data class ResumenSync(
    val pendientes: Int = 0,
    val conDependencia: Int = 0,
    val conError: Int = 0,
    val ultimoSyncOk: LocalDateTime? = null,
    val catalogosVacios: Boolean = false,
)

class ObservarResumenSyncUseCase(
    private val observarPendientesUseCase: ObservarPendientesUseCase,
    private val catalogoRepository: CatalogoRepository,
) {
    operator fun invoke(): Flow<ResumenSync> = combine(
        observarPendientesUseCase(),
        catalogoRepository.observarTiposQueso(),
    ) { resumen, tiposQueso ->
        val estados = resumen.registros.map { it.estado } + resumen.analisis.map { it.estado } +
            resumen.lotes.map { it.estado } + resumen.ventas.map { it.estado }
        ResumenSync(
            pendientes = estados.count { it is EstadoSincronizacion.Pendiente || it is EstadoSincronizacion.Sincronizando },
            conDependencia = estados.count { it is EstadoSincronizacion.EsperandoDependencia },
            conError = estados.count { it is EstadoSincronizacion.Fallido },
            ultimoSyncOk = tiposQueso.maxOfOrNull { it.actualizadoEn },
            catalogosVacios = tiposQueso.isEmpty(),
        )
    }
}
