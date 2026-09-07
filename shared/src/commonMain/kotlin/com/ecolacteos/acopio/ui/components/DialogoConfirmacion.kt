package com.ecolacteos.acopio.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ecolacteos.acopio.ui.theme.AcopioColores

/**
 * Confirmación que nombra explícitamente lo que se pierde (`MOBILE_SCREENS.md §13`) -- el componente
 * número 11, el único que faltaba. Nunca un "¿Estás seguro?" genérico: [mensaje] tiene que traer los datos
 * reales de lo que se va a perder (`S-05` regla 3: "Se va a borrar el registro de 120.50 L de Juan Pérez
 * del 4/9. No se puede deshacer"), armado por el `ViewModel`, no acá.
 */
@Composable
fun DialogoConfirmacion(
    titulo: String,
    mensaje: String,
    textoConfirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(mensaje) },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(textoConfirmar, color = AcopioColores.error, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        },
    )
}
