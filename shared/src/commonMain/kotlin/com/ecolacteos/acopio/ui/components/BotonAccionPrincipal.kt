package com.ecolacteos.acopio.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.ui.theme.Toques

/**
 * Botón grande de acción primaria (`MOBILE_SCREENS.md §13`), dimensionado para uso con guantes (`§15`:
 * mínimo 48dp, más en las acciones primarias -- acá [Toques.principal] = 56dp).
 */
@Composable
fun BotonAccionPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().height(Toques.principal.dp),
    ) {
        Text(texto, style = MaterialTheme.typography.titleLarge)
    }
}
