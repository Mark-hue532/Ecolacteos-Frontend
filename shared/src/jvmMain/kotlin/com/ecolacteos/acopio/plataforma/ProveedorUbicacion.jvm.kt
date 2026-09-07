package com.ecolacteos.acopio.plataforma

/** Target `jvm()` -- no hay GPS. Siempre `NoDisponible`, solo para que `:shared:jvmTest` compile. */
actual class ProveedorUbicacionDePlataforma : ProveedorUbicacion {
    actual override suspend fun obtenerUbicacionActual(): ResultadoUbicacion = ResultadoUbicacion.NoDisponible
}
