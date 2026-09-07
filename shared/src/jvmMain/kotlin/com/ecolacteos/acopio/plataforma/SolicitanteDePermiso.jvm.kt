package com.ecolacteos.acopio.plataforma

import androidx.compose.runtime.Composable

/** Target `jvm()` -- nunca hay diálogo de permisos que disparar; resuelve `DENEGADO` de inmediato. */
@Composable
actual fun rememberSolicitanteDePermiso(permiso: Permiso, onResultado: (EstadoPermiso) -> Unit): () -> Unit =
    { onResultado(EstadoPermiso.DENEGADO) }
