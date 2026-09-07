package com.ecolacteos.acopio.plataforma

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.ecolacteos.acopio.core.Decimal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * `LocationManager` puro del SDK (sección 3.2 del prompt: se descarta `play-services-location` a propósito,
 * para no atar la app a Google Play Services -- este proyecto ya corre sin ninguna dependencia de Google
 * fuera de lo estrictamente necesario). `requestLocationUpdates` con `minTime=0`/`minDistance=0` y remoción
 * en el primer resultado: es el equivalente de "una sola lectura", disponible desde API 1 -- `getCurrentLocation`
 * (más directo) recién existe desde API 30, por encima de `minSdk 26` de este proyecto.
 *
 * `context` por constructor -- mismo criterio que el resto de los `actual` de `plataforma/` para Android.
 */
actual class ProveedorUbicacionDePlataforma(private val context: Context) : ProveedorUbicacion {

    actual override suspend fun obtenerUbicacionActual(): ResultadoUbicacion {
        val concedido = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!concedido) return ResultadoUbicacion.SinPermiso

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return ResultadoUbicacion.NoDisponible
        val proveedor = mejorProveedorDisponible(locationManager) ?: return ResultadoUbicacion.NoDisponible

        return suspendCancellableCoroutine { continuacion ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuacion.isActive) continuacion.resume(location.aResultado())
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) {
                    locationManager.removeUpdates(this)
                    if (continuacion.isActive) continuacion.resume(ResultadoUbicacion.NoDisponible)
                }
            }

            try {
                locationManager.requestLocationUpdates(proveedor, 0L, 0f, listener, Looper.getMainLooper())
            } catch (sinPermiso: SecurityException) {
                // El permiso pudo revocarse entre el checkSelfPermission de arriba y esta llamada (ventana
                // angosta, pero real en Android -- el usuario puede revocar desde Ajustes en cualquier momento).
                continuacion.resume(ResultadoUbicacion.SinPermiso)
                return@suspendCancellableCoroutine
            }

            continuacion.invokeOnCancellation { locationManager.removeUpdates(listener) }
        }
    }

    private fun mejorProveedorDisponible(locationManager: LocationManager): String? = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
}

/**
 * Único punto de `androidMain` donde un `Double` de una API del SO se convierte a [Decimal] -- ver el
 * comentario de `ProveedorUbicacion.kt`. `Location.getLatitude()/getLongitude()` son `Double` por firma de
 * la plataforma (no existe una API de mayor precisión); `BigDecimal.fromDouble` evita el `String`
 * intermedio con notación científica que rompería `decimalDesdeTexto` para coordenadas cercanas a 0
 * (ej. `4.0E-4`, un caso real cerca del ecuador/meridiano de Greenwich).
 */
private fun Location.aResultado(): ResultadoUbicacion.Obtenida = ResultadoUbicacion.Obtenida(
    lat = Decimal.fromDouble(latitude),
    lng = Decimal.fromDouble(longitude),
)
