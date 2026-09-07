package com.ecolacteos.acopio.plataforma

import com.ecolacteos.acopio.core.Decimal

/** Resultado de un intento de fix de GPS (`A-04`, `MOBILE_SCREENS.md §5`: `EstadoGps`). Nunca lanza. */
sealed interface ResultadoUbicacion {
    data class Obtenida(val lat: Decimal, val lng: Decimal) : ResultadoUbicacion
    data object SinPermiso : ResultadoUbicacion
    data object NoDisponible : ResultadoUbicacion
}

/**
 * Un solo fix de ubicación (`A-04`). `interface` (no un `expect` a secas) por el mismo motivo que
 * `ConnectivityObserver`/`GestorPermisos`: `commonTest` inyecta un fake -- acá es literal el "proveedor de
 * ubicación falso" que pide `PROMPT_FASE_08A.md §3.1`.
 *
 * El timeout de 15 s de `§5` **no** vive acá: lo aplica `RegistrarAcopioViewModel` con `withTimeoutOrNull`,
 * igual que la política de reintentos de sync no vive en `ApiClient` (`§4.3`) -- esta interfaz solo sabe
 * pedir un fix, no cuánto esperar.
 */
interface ProveedorUbicacion {
    /**
     * Intenta obtener la posición actual. `SinPermiso` si el permiso no está concedido -- no dispara el
     * diálogo del sistema por su cuenta (eso es `plataforma/SolicitanteDePermiso.kt`, ver la nota de
     * `GestorPermisos.kt`). Puede suspender indefinidamente mientras espera un fix; el llamador es quien
     * debe envolver la llamada en un timeout.
     */
    suspend fun obtenerUbicacionActual(): ResultadoUbicacion
}

/**
 * Implementación real por plataforma: Android `LocationManager` (sin `play-services-location`, decisión de
 * la sección 3.2 del prompt -- evita atar la app a Google Play Services), iOS `CLLocationManager` por
 * cinterop. Sin constructor común -- mismo patrón que el resto de esta fase. El `actual` de `jvm()` siempre
 * devuelve `NoDisponible`, solo para que `:shared:jvmTest` compile.
 *
 * El único punto de todo `commonMain`/`androidMain`/`iosMain` donde un `Double` de la API del SO
 * (`Location.getLatitude()`/`CLLocationCoordinate2D`) se convierte a [Decimal] -- ver el comentario en cada
 * `actual` para la justificación exacta de por qué esto no viola `CLAUDE.md §3.1`.
 */
expect class ProveedorUbicacionDePlataforma : ProveedorUbicacion {
    override suspend fun obtenerUbicacionActual(): ResultadoUbicacion
}
