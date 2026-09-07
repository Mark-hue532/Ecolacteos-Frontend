package com.ecolacteos.acopio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.cash.sqldelight.db.SqlDriver
import com.ecolacteos.acopio.data.local.AcopioDriverFactory
import com.ecolacteos.acopio.di.initKoin
import com.ecolacteos.acopio.plataforma.GestorPermisos
import com.ecolacteos.acopio.plataforma.GestorPermisosDePlataforma
import com.ecolacteos.acopio.plataforma.ProveedorUbicacion
import com.ecolacteos.acopio.plataforma.ProveedorUbicacionDePlataforma
import com.ecolacteos.acopio.security.AlmacenamientoSeguroDeSesion
import com.ecolacteos.acopio.security.SecureTokenStorage
import com.ecolacteos.acopio.synchronization.ConnectivityObserver
import com.ecolacteos.acopio.synchronization.ConnectivityObserverDePlataforma
import com.ecolacteos.acopio.ui.App
import org.koin.dsl.module

/**
 * Contenedor delgado (CLAUDE.md §3.5): arranca Koin y monta `App()` de `shared/ui/` -- ninguna lógica de
 * presentación vive acá (`CLAUDE.md §3.5`, trampa #3 de `PROMPT_FASE_07.md`). `ComponentActivity`, no
 * `Activity` a secas (Fase 1): `setContent` (Compose) es una extensión de `ComponentActivity`.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Los bindings de acá son específicos de esta plataforma (necesitan Context, que commonMain no
        // puede proveer -- ver `security/SecureTokenStorage.kt`, `data/local/AcopioDriverFactory.kt`,
        // `synchronization/ConnectivityObserver.kt`, `plataforma/GestorPermisos.kt`/`ProveedorUbicacion.kt`
        // (Fase 8A), y los módulos de Koin de Fase 3/4/5/6/8A que los consumen sin declararlos).
        // `applicationContext`, no `this`, para no atar la vida de estos singletons de Koin a esta Activity
        // (fuga de memoria clásica -- trampa #10 de `PROMPT_FASE_06.md`).
        val contextoAplicacion = applicationContext
        initKoin {
            modules(
                module {
                    single<AlmacenamientoSeguroDeSesion> { SecureTokenStorage(contextoAplicacion) }
                    single<SqlDriver> { AcopioDriverFactory(contextoAplicacion).crearDriver() }
                    single<ConnectivityObserver> { ConnectivityObserverDePlataforma(contextoAplicacion) }
                    single<GestorPermisos> { GestorPermisosDePlataforma(contextoAplicacion) }
                    single<ProveedorUbicacion> { ProveedorUbicacionDePlataforma(contextoAplicacion) }
                },
            )
        }

        setContent {
            App()
        }
    }
}
