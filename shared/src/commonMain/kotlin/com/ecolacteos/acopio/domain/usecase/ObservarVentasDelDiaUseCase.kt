package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.data.repository.VentaRepository
import com.ecolacteos.acopio.domain.model.Venta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * `V-01 Home ventas` (`MOBILE_SCREENS.md §8`): "ventas del día". Filtra por [Venta.fecha] -- el campo que
 * el propio dispositivo/usuario ingresa en `V-02`, nunca por `creadoEn`/`sincronizadoEn` (`§10.3`: no
 * mezclar marcos temporales). `creadoEn` de `Venta` tampoco es un campo de servidor (lo pone el propio
 * `VentaRepositoryImpl.crear()` con la hora del dispositivo al capturar), así que ordenar por él como
 * desempate no viola `§10.3` -- pero el filtro "de hoy" en sí usa exclusivamente `fecha`, tal cual pide la
 * regla.
 *
 * Reusa `VentaRepository.observarPendientes()` (Fase 6): pese al nombre, ya observa **todas** las ventas
 * del usuario activo, no solo las no sincronizadas (ver su implementación) -- no hay endpoint ni motivo
 * para volver a pedirle nada a la red acá (`§8`: "Modo offline OK", ver la resolución de la contradicción
 * de `§12` en el checkpoint de la Fase 7).
 */
class ObservarVentasDelDiaUseCase(
    private val repository: VentaRepository,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(): Flow<List<Venta>> = repository.observarPendientes().map { ventas ->
        val hoy = ahoraComoFechaHora(reloj, zona).date
        ventas.filter { it.fecha == hoy }.sortedByDescending { it.creadoEn }
    }
}
