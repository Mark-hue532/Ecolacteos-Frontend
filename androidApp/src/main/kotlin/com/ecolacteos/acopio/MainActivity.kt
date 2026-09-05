package com.ecolacteos.acopio

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.ecolacteos.acopio.di.initKoin

/**
 * Contenedor delgado (CLAUDE.md §3.5): solo arranca Koin y monta la app. Sin UI de Compose todavía --
 * eso es Fase 7. `activity: Activity` en vez de `AppCompatActivity` porque no hay dependencia de
 * AndroidX AppCompat declarada en esta fase (fuera de alcance -- ver `libs.versions.toml`).
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initKoin()

        setContentView(
            TextView(this).apply {
                text = getString(R.string.placeholder_fase_1)
            },
        )
    }
}
