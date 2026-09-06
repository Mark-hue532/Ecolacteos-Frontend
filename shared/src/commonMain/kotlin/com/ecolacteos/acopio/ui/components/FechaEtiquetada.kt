package com.ecolacteos.acopio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Fecha con su etiqueta de origen (`MOBILE_SCREENS.md §13`, cumple `§10.3`): "Capturado 04/09 16:20" en vez
 * de una fecha suelta sin decir de dónde salió -- crítico mientras `DATA-001` siga abierto, para no dar a
 * entender que dos fechas de marcos distintos son comparables.
 */
@Composable
fun FechaEtiquetada(etiqueta: String, fechaTexto: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge)
        Text(fechaTexto, style = MaterialTheme.typography.bodyMedium)
    }
}
