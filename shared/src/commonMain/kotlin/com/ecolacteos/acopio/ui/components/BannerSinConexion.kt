package com.ecolacteos.acopio.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.ui.theme.AcopioColores

/**
 * Banner informativo no bloqueante (`MOBILE_SCREENS.md §10.6` punto 4, §13) para pantallas offline-first.
 * Discreto y persistente (`§4`, `S-03`) -- nunca un diálogo.
 */
@Composable
fun BannerSinConexion(modifier: Modifier = Modifier) {
    Surface(color = AcopioColores.atencion.copy(alpha = 0.15f), modifier = modifier.fillMaxWidth()) {
        Text(
            "Sin conexión. Tu trabajo se guarda igual.",
            color = AcopioColores.atencion,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}
