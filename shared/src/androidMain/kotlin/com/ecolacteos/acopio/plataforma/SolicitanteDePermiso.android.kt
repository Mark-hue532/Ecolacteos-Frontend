package com.ecolacteos.acopio.plataforma

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat

/**
 * `ActivityResultContracts.RequestPermission()` (`androidx.activity.compose`, ver `CLAUDE.md §4`) --
 * único mecanismo moderno de Android para disparar el diálogo real, y por eso solo existe como
 * `@Composable` (necesita el `ActivityResultRegistry` de la `ComponentActivity` viva, `LocalActivity`
 * agregado en `androidx-activity-compose 1.9+`).
 *
 * Distinción `DENEGADO`/`DENEGADO_PERMANENTE` (`§12` regla 3): el idiom estándar de Android es consultar
 * `shouldShowRequestPermissionRationale` **después** del resultado, no antes -- si el sistema dice que no
 * hay que mostrar la explicación de nuevo justo después de haber preguntado, es porque el usuario marcó
 * "no preguntar de nuevo" (o el dispositivo la denegó por política), y la única salida son los ajustes.
 */
@Composable
actual fun rememberSolicitanteDePermiso(permiso: Permiso, onResultado: (EstadoPermiso) -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val permisoAndroid = permiso.aPermisoAndroid()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        val estado = when {
            concedido -> EstadoPermiso.CONCEDIDO
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permisoAndroid) ->
                EstadoPermiso.DENEGADO
            else -> EstadoPermiso.DENEGADO_PERMANENTE
        }
        onResultado(estado)
    }

    return { launcher.launch(permisoAndroid) }
}
