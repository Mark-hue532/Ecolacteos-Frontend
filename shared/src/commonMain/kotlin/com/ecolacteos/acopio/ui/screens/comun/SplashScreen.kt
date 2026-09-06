package com.ecolacteos.acopio.ui.screens.comun

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ecolacteos.acopio.presentation.comun.DestinoSplash
import com.ecolacteos.acopio.presentation.comun.SplashEffect
import com.ecolacteos.acopio.presentation.comun.SplashViewModel
import kotlinx.coroutines.flow.collect
import org.koin.compose.viewmodel.koinViewModel

/** `S-01 · Splash` (`MOBILE_SCREENS.md §4`). Máximo ~800ms antes del indicador -- acá siempre lo muestra. */
@Composable
fun SplashScreen(onNavegar: (DestinoSplash) -> Unit, viewModel: SplashViewModel = koinViewModel()) {
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { efecto ->
            when (efecto) {
                is SplashEffect.Navegar -> onNavegar(efecto.destino)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
