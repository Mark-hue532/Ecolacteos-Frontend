package com.ecolacteos.acopio.presentation.comun

import com.ecolacteos.acopio.core.ApiError
import com.ecolacteos.acopio.core.ApiResult
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.ResultadoCierreSesion
import com.ecolacteos.acopio.domain.Sesion
import com.ecolacteos.acopio.domain.usecase.LoginUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.synchronization.ConnectivityObserverFake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** `GestorSesion` que nunca resuelve la sesión -- alcanza para probar Login sin las 4 Repository de VerificarPendientes. */
private class GestorSesionDeLogin(private val respuesta: () -> ApiResult<Sesion>) : GestorSesion {
    override val sesion: StateFlow<Sesion?> = MutableStateFlow(null)
    override suspend fun iniciarSesion(email: String, password: String): ApiResult<Sesion> = respuesta()
    override suspend fun sesionActual(): Sesion? = null
    override suspend fun estaVigente(): Boolean = false
    override suspend fun refrescarSiHaceFalta(): ApiResult<Unit> = ApiResult.Exito(Unit)
    override suspend fun cerrarSesion(): ResultadoCierreSesion = ResultadoCierreSesion.CERRADA
    override suspend fun invalidarSesion() = Unit
}

/** Tests de `S-02` (`PROMPT_FASE_07.md §9`, parte del punto 5 -- bloqueo online-only -- y el 401 de login). */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sin conexion el boton queda deshabilitado y no se intenta la llamada`() = runTest {
        var vecesLlamado = 0
        val gestorSesion = GestorSesionDeLogin {
            vecesLlamado++
            error("no debería llamarse -- S-02 es online-only")
        }
        val conectividad = ConnectivityObserverFake(inicial = false)
        val viewModel = LoginViewModel(LoginUseCase(gestorSesion), ObservarConectividadUseCase(conectividad))

        viewModel.onEvent(LoginEvent.EmailCambio("ana@ecolacteos.com"))
        viewModel.onEvent(LoginEvent.PasswordCambio("clave-valida"))

        assertFalse(viewModel.uiState.value.puedeEnviar)
        viewModel.onEvent(LoginEvent.EnviarPresionado)
        assertEquals(0, vecesLlamado)
    }

    @Test
    fun `un 401 nunca distingue si fallo el correo o la contrasena`() = runTest {
        val gestorSesion = GestorSesionDeLogin { ApiResult.Error(ApiError.NoAutorizado("Credenciales inválidas")) }
        val viewModel = LoginViewModel(LoginUseCase(gestorSesion), ObservarConectividadUseCase(ConnectivityObserverFake(inicial = true)))

        viewModel.onEvent(LoginEvent.EmailCambio("ana@ecolacteos.com"))
        viewModel.onEvent(LoginEvent.PasswordCambio("clave-cualquiera"))
        viewModel.onEvent(LoginEvent.EnviarPresionado)

        assertEquals("Correo o contraseña incorrectos", viewModel.uiState.value.errorGeneral)
    }
}
