package com.ecolacteos.acopio.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS no tiene un contrato relanzable como `ActivityResultContracts` -- se pide directo contra
 * `AVFoundation`/`CoreLocation` y se resuelve por callback/delegate, envuelto en una corrutina.
 *
 * ⚠️ Compila pero no se verificó en simulador/dispositivo desde esta sesión (Windows, `CLAUDE.md §8`).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberSolicitanteDePermiso(permiso: Permiso, onResultado: (EstadoPermiso) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val estado = when (permiso) {
                Permiso.CAMARA -> solicitarPermisoCamaraIos()
                Permiso.UBICACION -> solicitarPermisoUbicacionIos()
            }
            onResultado(estado)
        }
    }
}

/**
 * iOS solo muestra el diálogo de cámara **una vez** por instalación (`§12` regla 3): un `false` acá ya es
 * definitivo -- a diferencia de Android no existe un "denegado, se puede reintentar" intermedio.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun solicitarPermisoCamaraIos(): EstadoPermiso = suspendCancellableCoroutine { continuacion ->
    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { concedido ->
        if (continuacion.isActive) {
            continuacion.resume(if (concedido) EstadoPermiso.CONCEDIDO else EstadoPermiso.DENEGADO_PERMANENTE)
        }
    }
}

/**
 * `CLLocationManagerDelegateProtocol.locationManagerDidChangeAuthorization` es la única forma de saber
 * cuándo el usuario respondió el diálogo de `requestWhenInUseAuthorization()` -- no hay callback directo.
 *
 * ⚠️ `manager`/`delegado` se retienen en variables de nivel de archivo (no locales a la función) para
 * reducir el riesgo de que ARC/el GC de Kotlin/Native los libere mientras el diálogo sigue abierto -- un
 * caso de borde que no se pudo ejercitar en runtime desde esta sesión (Windows). Revisar en una Mac antes
 * de confiar en el flujo completo.
 */
private var managerDeUbicacionActivo: CLLocationManager? = null
private var delegadoDeUbicacionActivo: CLLocationManagerDelegateProtocol? = null

@OptIn(ExperimentalForeignApi::class)
private suspend fun solicitarPermisoUbicacionIos(): EstadoPermiso = suspendCancellableCoroutine { continuacion ->
    val manager = CLLocationManager()
    val delegado = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            val estado = manager.authorizationStatus.aEstadoPermiso() ?: return
            if (continuacion.isActive) continuacion.resume(estado)
            managerDeUbicacionActivo = null
            delegadoDeUbicacionActivo = null
        }
    }
    managerDeUbicacionActivo = manager
    delegadoDeUbicacionActivo = delegado
    manager.delegate = delegado
    manager.requestWhenInUseAuthorization()
}

@OptIn(ExperimentalForeignApi::class)
private fun CLAuthorizationStatus.aEstadoPermiso(): EstadoPermiso? = when (this) {
    kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> EstadoPermiso.CONCEDIDO
    kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> EstadoPermiso.DENEGADO_PERMANENTE
    kCLAuthorizationStatusNotDetermined -> null // el usuario todavía no respondió el diálogo
    else -> EstadoPermiso.DENEGADO_PERMANENTE
}
