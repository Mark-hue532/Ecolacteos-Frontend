package com.ecolacteos.acopio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.presentation.MensajeError
import com.ecolacteos.acopio.ui.theme.AcopioColores

/**
 * Error distinguiendo red (reintentable, con botón) de negocio (`MOBILE_SCREENS.md §10.6` punto 3, §13).
 * Recibe [MensajeError] ya mapeado (`§10.4`) -- nunca un `ApiError` (`§3.3`).
 */
@Composable
fun EstadoError(error: MensajeError, modifier: Modifier = Modifier, onReintentar: (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(error.texto, color = AcopioColores.error, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (error.reintentable && onReintentar != null) {
            Button(onClick = onReintentar) { Text("Reintentar") }
        }
    }
}
