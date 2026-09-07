package com.ecolacteos.acopio.domain.usecase

/**
 * La regla de retención agregada de `P-02` (`PROMPT_FASE_08D.md §4.1`, `MOBILE_SCREENS.md §7`): **basta
 * una** entrega propia sin sincronizar entre las seleccionadas para que el lote entero quede
 * `PENDING_DEPENDENCY` -- coincide exactamente con lo que `LoteProduccionRepositoryImpl.crear()` decide al
 * guardar (`todosConIdDeServidor`), pero acá se calcula **antes** de guardar, para el aviso de `C-02`/`§4.1`
 * (`DATA-003`: al seleccionar, no al guardar). Va en dominio, no en el `ViewModel` (trampa #2) -- mismo
 * criterio que `ClasificarPadresRegistroAcopioUseCase` de `8C`.
 */
data class RetencionAgregadaLote(
    val seraRetenido: Boolean,
    val pendientesDeSync: Int,
    val total: Int,
)

fun evaluarRetencionAgregada(seleccion: List<PadreRegistroAcopioElegible>): RetencionAgregadaLote {
    val pendientes = seleccion.count { it is PadreRegistroAcopioElegible.PropioPendienteDeSync }
    return RetencionAgregadaLote(seraRetenido = pendientes > 0, pendientesDeSync = pendientes, total = seleccion.size)
}
