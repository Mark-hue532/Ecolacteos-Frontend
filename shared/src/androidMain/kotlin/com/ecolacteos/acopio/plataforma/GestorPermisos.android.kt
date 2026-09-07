package com.ecolacteos.acopio.plataforma

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/** `internal`, no `private`: `SolicitanteDePermiso.android.kt` (mismo módulo) también necesita el mapeo. */
internal fun Permiso.aPermisoAndroid(): String = when (this) {
    Permiso.CAMARA -> Manifest.permission.CAMERA
    Permiso.UBICACION -> Manifest.permission.ACCESS_FINE_LOCATION
}

/**
 * `context` por constructor, lo arma el módulo de Koin de `androidApp` (mismo criterio que
 * `SecureTokenStorage.android.kt`/`ConnectivityObserverDePlataforma`). `applicationContext`, no la
 * Activity: `checkSelfPermission` y el intent de ajustes no necesitan una Activity viva.
 */
actual class GestorPermisosDePlataforma(private val context: Context) : GestorPermisos {

    actual override fun tieneConcedido(permiso: Permiso): Boolean =
        ContextCompat.checkSelfPermission(context, permiso.aPermisoAndroid()) == PackageManager.PERMISSION_GRANTED

    actual override fun abrirAjustesDeLaApp() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            // FLAG_ACTIVITY_NEW_TASK obligatorio: se lanza desde applicationContext, no desde una Activity.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
