package com.ecolacteos.acopio.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Centraliza el `@BeforeTest`/`@AfterTest` de `Dispatchers.setMain`/`resetMain` que cada test de
 * `ViewModel` repetía a mano. **`registrar()` NO libera el `ViewModel` hoy** -- ver `PROMPT_FASE_08D.md
 * §1.1` para el intento completo y por qué se revirtió; queda como no-op documentado a propósito, para no
 * tener que volver a tocar los ~22 archivos que ya llaman `viewModels.registrar(...)` el día que se
 * desbloquee.
 *
 * **Por qué no libera de verdad (checkpoint de `8D`, hallazgo bloqueante)**: el mecanismo estándar es
 * `ViewModelStore().apply { put(key, viewModel) }.clear()` -- pública, y al vivir en el mismo módulo que
 * `ViewModel` invoca su `clear()`/`onCleared()` internos (`protected`/`internal`, no llamables directo).
 * Se implementó así y **rompió `:shared:jvmTest` con `NoSuchMethodError` en el 100% de los tests de
 * `ViewModel`**, no de forma intermitente: `jvmTestCompileClasspath` resuelve
 * `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.6` (`androidx.lifecycle:lifecycle-viewmodel-desktop:2.9.4`,
 * `ViewModelStore.put(String, ViewModel)`), pero `jvmTestRuntimeClasspath` resuelve
 * `2.9.6 -> 2.11.0-beta01 -> 2.11.0` (`lifecycle-viewmodel-desktop:2.11.0`, confirmado con `javap`:
 * `ViewModelStore.put(Object, ViewModel)` -- la clave pasó de `String` a `Any`, cambio de ABI real, no un
 * problema de este código). La build de Compose Multiplatform 1.12.0 parece exigir esa versión más nueva
 * en runtime aunque el catálogo declare `2.9.6` -- contradice el pin de `CLAUDE.md §4`, y decidir si se
 * sube el catálogo (y a cuál versión exacta) es una decisión de toolchain que no me correspondía tomar en
 * silencio (`CLAUDE.md §4`: "si alguna no resuelve o es incompatible, parás y lo reportás").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextoDeViewModelsDePrueba {

    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    /**
     * No-op a propósito -- ver el comentario de la clase. Se deja el nombre y la firma para que los ~22
     * call sites (`viewModels.registrar(XxxViewModel(...))`) no necesiten tocarse de nuevo cuando esto se
     * resuelva: en ese momento, esta función pasa a envolver un `ViewModelStore` real.
     */
    fun <T : ViewModel> registrar(viewModel: T): T = viewModel

    fun finalizar() {
        Dispatchers.resetMain()
    }
}
