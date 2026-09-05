package com.ecolacteos.acopio.data.local

import com.ecolacteos.acopio.core.DispatcherProvider
import kotlinx.coroutines.Dispatchers

/**
 * [DispatcherProvider] de test para los `LocalDataSource` (`PROMPT_FASE_04.md §7`): `Dispatchers.Unconfined`
 * en las tres propiedades, igual criterio que documenta `core/DispatcherProvider.kt` para tests con
 * `UnconfinedTestDispatcher` -- acá basta el `Unconfined` real (multiplataforma, sin dependencia de test)
 * porque estos tests no verifican *timing*, solo que `Query.asFlow().mapToList(...)` emite lo correcto.
 */
object DispatcherProviderDeTest : DispatcherProvider {
    override val default = Dispatchers.Unconfined
    override val io = Dispatchers.Unconfined
    override val main = Dispatchers.Unconfined
}
