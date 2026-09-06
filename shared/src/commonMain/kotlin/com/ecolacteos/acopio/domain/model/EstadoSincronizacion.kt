package com.ecolacteos.acopio.domain.model

/**
 * Proyección a nivel de dominio del `SyncStatus` que persiste SQLite (`PROMPT_FASE_06.md §7`, no
 * negociable). Existe **por separado** de [SyncStatus] -- que sigue siendo el tipo persistido, Fase 4 --
 * porque acá se agrega información que SQLite no modela como estado propio: por qué está esperando, y si
 * un fallo es reintentable solo o necesita al usuario.
 *
 * ⚠️ La razón de ser de esta clase es `DATA-014` (Fase 5): un `PENDING_DEPENDENCY` puede ser una espera
 * legítima ("el padre va a sincronizar solo") o un bloqueo real ("el padre ya sincronizó pero el backend
 * nunca va a mandar su id"). Colapsar los dos en un `EsperandoDependencia` genérico sin [motivoConocido]
 * es exactamente lo que esta fase no puede hacer -- ver `RepositorioMappers.kt`.
 */
sealed class EstadoSincronizacion {

    /** Creado en SQLite, sin dependencias sin resolver, todavía no enviado (§6.1). */
    data object Pendiente : EstadoSincronizacion()

    /**
     * Retenido a propósito (C-03, §18.1): referencia un `RegistroAcopio` padre que todavía no tiene
     * `server_id` resoluble. [motivoConocido] es el `sync_error` tal cual lo dejó el Sync Engine -- nunca
     * se descarta ni se reemplaza por un texto genérico (§7 de esta fase, literal). Puede ser `null` en la
     * ventana muy breve entre que el Repository crea la fila y el primer ciclo la evalúa.
     */
    data class EsperandoDependencia(val motivoConocido: String?) : EstadoSincronizacion()

    /** En vuelo -- un `POST` de lote para este ítem está en curso o quedó huérfano de un ciclo anterior. */
    data object Sincronizando : EstadoSincronizacion()

    /** Confirmado por el backend (`confirmados[]`). De solo lectura desde acá en adelante. */
    data object Sincronizado : EstadoSincronizacion()

    /**
     * [reintentable] distingue si el Sync Engine lo va a reintentar solo (`next_attempt_at` programado,
     * §6.3) de si requiere que el usuario actúe -- ya sea porque se agotaron los intentos automáticos, o
     * porque vino en `errores[]` como fallo permanente de negocio. En cualquiera de los dos casos,
     * [ReintentarManualUseCase] puede forzar un reintento; `reintentable = false` no significa "sin salida",
     * significa "no va a pasar solo".
     */
    data class Fallido(val motivo: String, val reintentable: Boolean) : EstadoSincronizacion()
}
