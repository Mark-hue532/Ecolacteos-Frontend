package com.ecolacteos.acopio.plataforma

/**
 * Target `jvm()` -- existe solo para que `:shared:jvmTest` compile (`PROMPT_FASE_08A.md §3.1`, trampa #2).
 * Nunca se empaqueta en producción: Android e iOS tienen sus `actual` reales. Ningún permiso está nunca
 * concedido en este target y "abrir ajustes" es un no-op -- mismo criterio que `ConnectivityObserver.jvm.kt`.
 */
actual class GestorPermisosDePlataforma : GestorPermisos {
    actual override fun tieneConcedido(permiso: Permiso): Boolean = false
    actual override fun abrirAjustesDeLaApp() {
        // No-op: no hay "ajustes del sistema" en el target de test JVM puro.
    }
}
