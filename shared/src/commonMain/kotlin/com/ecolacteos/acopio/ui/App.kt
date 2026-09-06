package com.ecolacteos.acopio.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ecolacteos.acopio.ui.navigation.AcopioNavHost
import com.ecolacteos.acopio.ui.theme.AcopioTheme

/**
 * Punto de montaje raíz de Compose Multiplatform (`MOBILE_ARCHITECTURE.md §15`, `PROMPT_FASE_07.md §2.1`).
 * `androidApp/MainActivity.kt` la monta con `setContent { App() }`; la función de entrada que `iosApp`
 * consumirá cuando exista el `.xcodeproj` (`CLAUDE.md §8`) es esta misma, expuesta en el framework de
 * `shared`. Sin envoltorio de Koin acá a propósito: `initKoin()` (`di/Koin.kt`) ya arranca un `Koin`
 * *global* (`startKoin`, no el `KoinApplication {}` composable de `koin-compose`) antes de que
 * `MainActivity` llame a `setContent` -- cada `koinViewModel()` de `ui/screens/` lo resuelve solo.
 */
@Composable
fun App() {
    AcopioTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AcopioNavHost()
        }
    }
}
