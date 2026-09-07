package com.ecolacteos.acopio.plataforma

import androidx.compose.runtime.Composable

/**
 * La mitad de "permisos del SO" que sí necesita un `@Composable` (ver `GestorPermisos.kt`): dispara el
 * diálogo real del sistema y resuelve a un [EstadoPermiso] ya interpretado (`CONCEDIDO`/`DENEGADO`/
 * `DENEGADO_PERMANENTE`), no un simple `Boolean` -- la pantalla necesita la distinción de `§12` regla 3
 * para decidir si ofrece "reintentar" o "ir a ajustes".
 *
 * Devuelve una función disparadora en vez de lanzar el diálogo ella misma al componerse: quien la usa
 * decide **cuándo** (`§12` regla 1, "en contexto, nunca al arrancar") -- `A-02` la dispara al tocar
 * "Permitir cámara" en su pantalla de explicación; `A-04` la dispara una vez, automáticamente, al entrar
 * (`§5`: "se pide la ubicación al abrir la pantalla").
 *
 * `CLAUDE.md §3.4`/trampa #15 de `PROMPT_FASE_08A.md`: el resultado se reporta al `ViewModel` como
 * `Event` (nunca se decide nada de negocio acá), y quien invoca la función disparadora es siempre un
 * `LaunchedEffect`/`onClick` reaccionando a un `Effect` del `ViewModel` -- nunca lógica propia del
 * `@Composable`.
 */
@Composable
expect fun rememberSolicitanteDePermiso(permiso: Permiso, onResultado: (EstadoPermiso) -> Unit): () -> Unit
