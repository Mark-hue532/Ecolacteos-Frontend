package com.ecolacteos.acopio.plataforma

/**
 * Consulta de estado + acceso a ajustes del sistema (`MOBILE_SCREENS.md §12`) -- la mitad de "permisos del
 * SO" que no necesita una `Activity`/Composable (`CLAUDE.md §3.5`, mismo criterio que
 * `synchronization/ConnectivityObserver.kt`). La otra mitad, **solicitar** el permiso (disparar el diálogo
 * del sistema), no vive acá: en Android depende de `rememberLauncherForActivityResult`, que solo existe en
 * un `@Composable` -- ver `plataforma/SolicitanteDePermiso.kt` y el `Effect`/`Event` de cada `ViewModel`
 * (`MOBILE_SCREENS.md §3.1`: "pedir un permiso" es justo el ejemplo de `Effect` que da el propio documento).
 *
 * `interface` (no un `expect` a secas) por el mismo motivo que `ConnectivityObserver`: `commonTest` inyecta
 * un fake sin tocar nada de plataforma.
 */
interface GestorPermisos {
    /** Estado actual real (`ContextCompat.checkSelfPermission`/`AVCaptureDevice`/`CLLocationManager`). */
    fun tieneConcedido(permiso: Permiso): Boolean

    /** `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` / `UIApplicationOpenSettingsURLString` (`§12` regla 3). */
    fun abrirAjustesDeLaApp()
}

/**
 * Implementación real por plataforma. Sin constructor común (Android necesita `Context`, iOS no necesita
 * nada) -- mismo patrón que `ConnectivityObserverDePlataforma`/`SecureTokenStorage`/`AcopioDriverFactory`.
 * El `actual` de `jvm()` siempre devuelve `false`/no-op, solo para que `:shared:jvmTest` compile
 * (`PROMPT_FASE_08A.md §3.1`, trampa #2) -- ningún test de esta fase depende de este `actual`, usan
 * [GestorPermisos] fakeado directo.
 */
expect class GestorPermisosDePlataforma : GestorPermisos {
    override fun tieneConcedido(permiso: Permiso): Boolean
    override fun abrirAjustesDeLaApp()
}
