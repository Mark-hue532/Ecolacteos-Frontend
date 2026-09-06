package com.ecolacteos.acopio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.ui.theme.AcopioColores

/**
 * Indicador de la barra superior (`MOBILE_SCREENS.md §10.5`), presente en todas las pantallas autenticadas.
 * El éxito no se anuncia (`§10.5`): si [pendientes] es 0, [conError] es 0 y hay señal, no dibuja nada.
 */
@Composable
fun IndicadorSync(
    pendientes: Int,
    conError: Boolean,
    hayConexion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texto = when {
        conError -> "Atención: hay envíos con error"
        pendientes > 0 && !hayConexion -> "$pendientes por enviar · sin conexión"
        pendientes > 0 -> "$pendientes por enviar"
        else -> null
    } ?: return

    val color = if (conError) AcopioColores.error else AcopioColores.atencion
    Text(texto, color = color, style = MaterialTheme.typography.labelLarge, modifier = modifier.clickable(onClick = onClick).padding(8.dp))
}
