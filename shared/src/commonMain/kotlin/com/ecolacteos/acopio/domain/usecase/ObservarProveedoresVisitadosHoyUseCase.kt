package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * "Ya visitado hoy" de `A-01` (`MOBILE_SCREENS.md §5`): cruce entre la ruta del día y
 * `registro_acopio_local`. Decisión del checkpoint de la Fase 8A -- vive acá como `UseCase` propio (no en
 * el `ViewModel` ni en el `LocalDataSource`) porque es una regla de negocio ("visitado" = tiene una entrega
 * de hoy) reutilizable, y porque comparar contra "hoy" usa el reloj del dispositivo -- el mismo criterio
 * que el resto del proyecto aplica a `fechaHora` (`§10.3`, `DATA-001`/`DATA-012`): nunca se compara contra
 * un timestamp de servidor.
 *
 * Reusa `RegistroAcopioRepository.observarPendientes()` (pese al nombre, expone **todos** los registros de
 * la sesión activa, no solo los pendientes -- ver su propio doc) en vez de agregar un método nuevo al
 * Repository: el filtro por fecha es la única lógica que falta, y es puramente de presentación de esta
 * pantalla.
 */
class ObservarProveedoresVisitadosHoyUseCase(
    private val registroAcopioRepository: RegistroAcopioRepository,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(): Flow<Set<String>> = registroAcopioRepository.observarPendientes().map { registros ->
        val hoy = ahoraComoFechaHora(reloj, zona).date
        registros.filter { it.fechaHora.date == hoy }.map { it.proveedorId }.toSet()
    }
}
