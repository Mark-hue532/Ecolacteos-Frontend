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

/**
 * Vacío con causa y salida (`MOBILE_SCREENS.md §10.6` punto 2, §13). Nunca una lista en blanco sin
 * explicación. [textoAccion]/[onAccion] son opcionales: un vacío "positivo" (ej. `S-04` "Todo al día") no
 * necesita botón.
 */
@Composable
fun EstadoVacio(
    titulo: String,
    modifier: Modifier = Modifier,
    explicacion: String? = null,
    textoAccion: String? = null,
    onAccion: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        explicacion?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        if (textoAccion != null && onAccion != null) {
            Button(onClick = onAccion) { Text(textoAccion) }
        }
    }
}
