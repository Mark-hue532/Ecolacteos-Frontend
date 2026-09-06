package com.ecolacteos.acopio

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import app.cash.sqldelight.db.SqlDriver
import com.ecolacteos.acopio.data.local.AcopioDriverFactory
import com.ecolacteos.acopio.di.initKoin
import com.ecolacteos.acopio.security.AlmacenamientoSeguroDeSesion
import com.ecolacteos.acopio.security.SecureTokenStorage
import com.ecolacteos.acopio.synchronization.ConnectivityObserver
import com.ecolacteos.acopio.synchronization.ConnectivityObserverDePlataforma
import org.koin.dsl.module

/**
 * Contenedor delgado (CLAUDE.md §3.5): solo arranca Koin y monta la app. Sin UI de Compose todavía --
 * eso es Fase 7. `activity: Activity` en vez de `AppCompatActivity` porque no hay dependencia de
 * AndroidX AppCompat declarada en esta fase (fuera de alcance -- ver `libs.versions.toml`).
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Los 3 bindings de acá son especificos de esta plataforma (necesitan Context, que commonMain no
        // puede proveer -- ver `security/SecureTokenStorage.kt`, `data/local/AcopioDriverFactory.kt`,
        // `synchronization/ConnectivityObserver.kt`, y los módulos de Koin de Fase 3/4/5/6 que los
        // consumen sin declararlos). `applicationContext`, no `this`, para no atar la vida de estos
        // singletons de Koin a esta Activity (fuga de memoria clásica -- trampa #10 de `PROMPT_FASE_06.md`).
        val contextoAplicacion = applicationContext
        initKoin {
            modules(
                module {
                    single<AlmacenamientoSeguroDeSesion> { SecureTokenStorage(contextoAplicacion) }
                    single<SqlDriver> { AcopioDriverFactory(contextoAplicacion).crearDriver() }
                    single<ConnectivityObserver> { ConnectivityObserverDePlataforma(contextoAplicacion) }
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
