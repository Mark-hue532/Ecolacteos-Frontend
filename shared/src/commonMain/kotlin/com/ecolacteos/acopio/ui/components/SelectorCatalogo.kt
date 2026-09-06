package com.ecolacteos.acopio.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Desplegable sobre una tabla `*_cache` (`MOBILE_SCREENS.md §13`), usado en todos los formularios.
 * [opciones] vacío es el caso "requiere sincronizar" -- se muestra como [EstadoVacio] embebido en vez de un
 * desplegable inútil, para que el usuario entienda por qué no puede elegir nada todavía.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectorCatalogo(
    opciones: List<T>,
    seleccionado: T?,
    onSeleccion: (T) -> Unit,
    etiquetaTexto: (T) -> String,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var expandido by remember { mutableStateOf(false) }

    if (opciones.isEmpty()) {
        EstadoVacio(
            titulo = "$etiqueta: sin datos",
            explicacion = "Necesitás sincronizar para descargar esta lista.",
            modifier = modifier,
        )
        return
    }

    ExposedDropdownMenuBox(expanded = expandido, onExpandedChange = { expandido = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = seleccionado?.let(etiquetaTexto) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { Text("▾") },
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(etiquetaTexto(opcion)) },
                    onClick = {
                        onSeleccion(opcion)
                        expandido = false
                    },
                )
            }
        }
    }
}
