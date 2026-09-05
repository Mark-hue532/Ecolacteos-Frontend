package com.ecolacteos.acopio

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.ecolacteos.acopio.di.initKoin
import com.ecolacteos.acopio.security.AlmacenamientoSeguroDeSesion
import com.ecolacteos.acopio.security.SecureTokenStorage
import org.koin.dsl.module

/**
 * Contenedor delgado (CLAUDE.md §3.5): solo arranca Koin y monta la app. Sin UI de Compose todavía --
 * eso es Fase 7. `activity: Activity` en vez de `AppCompatActivity` porque no hay dependencia de
 * AndroidX AppCompat declarada en esta fase (fuera de alcance -- ver `libs.versions.toml`).
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // El binding de SecureTokenStorage es especifico de esta plataforma (necesita Context, que
        // commonMain no puede proveer -- ver `security/SecureTokenStorage.kt` y `di/SecurityModule.kt`).
        // `applicationContext`, no `this`, para no atar la vida del singleton de Koin a esta Activity.
        val contextoAplicacion = applicationContext
        initKoin {
            modules(
                module {
                    single<AlmacenamientoSeguroDeSesion> { SecureTokenStorage(contextoAplicacion) }
                },
            )
        }

        setContentView(
            TextView(this).apply {
                text = getString(R.string.placeholder_fase_1)
            },
        )
    }
}
