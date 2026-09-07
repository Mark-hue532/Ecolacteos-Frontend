package com.ecolacteos.acopio.plataforma

import com.ecolacteos.acopio.core.Decimal
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * `CLLocationManager.requestLocation()` (un solo fix, iOS 9+ -- muy por debajo del deployment target 15.0
 * de este proyecto), vía `CLLocationManagerDelegateProtocol`. Sin dependencia externa: `CoreLocation` está
 * disponible por cinterop sin agregar nada (`§3.2` del prompt).
 *
 * ⚠️ Compila pero no se verificó en simulador/dispositivo desde esta sesión (Windows, `CLAUDE.md §8`). El
 * mismo riesgo de retención de `manager`/`delegado` que `SolicitanteDePermiso.ios.kt` documenta aplica acá
 * -- se mitiga igual, con referencias de nivel de archivo en vez de locales a la función.
 */
@OptIn(ExperimentalForeignApi::class)
actual class ProveedorUbicacionDePlataforma : ProveedorUbicacion {

    actual override suspend fun obtenerUbicacionActual(): ResultadoUbicacion {
        val estadoActual = CLLocationManager().authorizationStatus
        if (estadoActual != kCLAuthorizationStatusAuthorizedWhenInUse && estadoActual != kCLAuthorizationStatusAuthorizedAlways) {
            return ResultadoUbicacion.SinPermiso
        }

        return suspendCancellableCoroutine { continuacion ->
            val manager = CLLocationManager()
            val delegado = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                    val ubicacion = didUpdateLocations.lastOrNull() as? CLLocation
                    if (continuacion.isActive) {
                        continuacion.resume(ubicacion?.aResultado() ?: ResultadoUbicacion.NoDisponible)
                    }
                    liberarSesionActiva()
                }

                override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                    if (continuacion.isActive) continuacion.resume(ResultadoUbicacion.NoDisponible)
                    liberarSesionActiva()
                }
            }

            managerActivo = manager
            delegadoActivo = delegado
            manager.delegate = delegado
            manager.requestLocation()

            continuacion.invokeOnCancellation {
                manager.stopUpdatingLocation()
                liberarSesionActiva()
            }
        }
    }

    private fun liberarSesionActiva() {
        managerActivo = null
        delegadoActivo = null
    }

    private companion object {
        var managerActivo: CLLocationManager? = null
        var delegadoActivo: CLLocationManagerDelegateProtocol? = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.aResultado(): ResultadoUbicacion.Obtenida = coordinate.useContents {
    // Único punto de `iosMain` donde un Double de una API del SO se convierte a Decimal -- ver el
    // comentario de `ProveedorUbicacion.kt` y su equivalente Android (`ProveedorUbicacion.android.kt`).
    ResultadoUbicacion.Obtenida(lat = Decimal.fromDouble(latitude), lng = Decimal.fromDouble(longitude))
}
