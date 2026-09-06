package com.ecolacteos.acopio.ui.screens.comun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.comun.LoginEffect
import com.ecolacteos.acopio.presentation.comun.LoginEvent
import com.ecolacteos.acopio.presentation.comun.LoginViewModel
import com.ecolacteos.acopio.ui.components.BloqueoOnlineOnly
import com.ecolacteos.acopio.ui.components.BotonAccionPrincipal
import com.ecolacteos.acopio.ui.theme.Espaciado
import org.koin.compose.viewmodel.koinViewModel

/**
 * `S-02 · Login` (`MOBILE_SCREENS.md §4`, ONLINE-ONLY). Sin conexión se usa [BloqueoOnlineOnly] en vez del
 * formulario (`§13`: la tabla de "Usado en" no lista `S-02`, pero es una omisión -- `PROMPT_FASE_07.md
 * §2.3` pide construirlo acá y usarlo acá). Nunca se intenta la llamada sin señal (trampa #11 análoga).
 */
@Composable
fun LoginScreen(onLoginExitoso: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                LoginEffect.NavegarAHome -> onLoginExitoso()
            }
        }
    }

    if (!estado.hayConexion) {
        BloqueoOnlineOnly(mensaje = "Necesitás conexión para iniciar sesión")
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Espaciado.l.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Acopio", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = estado.email,
            onValueChange = { viewModel.onEvent(LoginEvent.EmailCambio(it)) },
            label = { Text("Correo") },
            isError = estado.errorEmail != null,
            supportingText = estado.errorEmail?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = Espaciado.m.dp),
        )

        OutlinedTextField(
            value = estado.password,
            onValueChange = { viewModel.onEvent(LoginEvent.PasswordCambio(it)) },
            label = { Text("Contraseña") },
            isError = estado.errorPassword != null,
            supportingText = estado.errorPassword?.let { { Text(it) } },
            visualTransformation = if (estado.verPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = Espaciado.s.dp),
        )

        estado.errorGeneral?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Espaciado.s.dp))
        }

        BotonAccionPrincipal(
            texto = if (estado.enviando) "Ingresando..." else "Ingresar",
            onClick = { viewModel.onEvent(LoginEvent.EnviarPresionado) },
            habilitado = estado.puedeEnviar,
            modifier = Modifier.padding(top = Espaciado.l.dp),
        )
    }
}
