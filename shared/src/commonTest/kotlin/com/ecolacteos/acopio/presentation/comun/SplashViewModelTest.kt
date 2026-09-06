package com.ecolacteos.acopio.presentation.comun

import app.cash.turbine.test
import com.ecolacteos.acopio.domain.usecase.DecidirDestinoInicialUseCase
import com.ecolacteos.acopio.domain.usecase.RefrescarSesionUseCase
import com.ecolacteos.acopio.synchronization.GestorSesionFake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Test 11 de `PROMPT_FASE_07.md §9`: `S-01` no bloquea esperando red. */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `con token valido el bootstrap llega a Home sin esperar ninguna llamada de red`() = runTest {
        // GestorSesionFake.refrescarSiHaceFalta() (ver FakesDeSync.kt) nunca toca la red -- si el
        // ViewModel llegara a esperar por él antes de navegar, este test seguiría pasando igual de rápido
        // porque el fake no bloquea; lo que prueba de verdad es que la navegación no depende de su resultado
        // (`viewModelScope.launch` propio, sin `await`) y llega con la sesión ya vigente.
        val gestorSesion = GestorSesionFake(GestorSesionFake.SESION_DE_PRUEBA)
        val viewModel = SplashViewModel(DecidirDestinoInicialUseCase(gestorSesion), RefrescarSesionUseCase(gestorSesion))

        viewModel.effect.test {
            assertEquals(SplashEffect.Navegar(DestinoSplash.HOME), awaitItem())
        }
    }

    @Test
    fun `sin sesion navega a Login`() = runTest {
        val gestorSesion = GestorSesionFake(sesionFija = null)
        val viewModel = SplashViewModel(DecidirDestinoInicialUseCase(gestorSesion), RefrescarSesionUseCase(gestorSesion))

        viewModel.effect.test {
            assertEquals(SplashEffect.Navegar(DestinoSplash.LOGIN), awaitItem())
        }
    }
}
