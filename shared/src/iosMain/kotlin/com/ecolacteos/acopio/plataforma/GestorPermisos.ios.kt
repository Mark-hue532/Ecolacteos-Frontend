package com.ecolacteos.acopio.plataforma

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * `AVCaptureDevice.authorizationStatusForMediaType`/`CLLocationManager.authorizationStatus` (`Security`/
 * `CoreLocation`, sin dependencia externa -- el prompt lo señala como lo "nativo" para iOS, `§3.2`).
 *
 * Sin constructor: iOS no necesita `Context` (mismo contraste que el resto de los `actual` de `plataforma/`
 * para iOS).
 *
 * ⚠️ Compila pero no se verificó en simulador/dispositivo desde esta sesión (Windows, `CLAUDE.md §8`).
 */
@OptIn(ExperimentalForeignApi::class)
actual class GestorPermisosDePlataforma : GestorPermisos {

    actual override fun tieneConcedido(permiso: Permiso): Boolean = when (permiso) {
        Permiso.CAMARA ->
            AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized
        Permiso.UBICACION -> {
            val estado: CLAuthorizationStatus = CLLocationManager().authorizationStatus
            estado == kCLAuthorizationStatusAuthorizedWhenInUse || estado == kCLAuthorizationStatusAuthorizedAlways
        }
    }

    actual override fun abrirAjustesDeLaApp() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }
}
