package com.ecolacteos.acopio.plataforma

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Target `jvm()` -- no hay cámara. Placeholder solo para que `:shared:jvmTest` compile. */
@Composable
actual fun EscanerQr(onCodigoDetectado: (String) -> Unit, modifier: Modifier) {
    Text("Cámara no disponible en este target", modifier = modifier.fillMaxSize().wrapContentSize(Alignment.Center))
}
