package com.ecolacteos.acopio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Bloqueo con explicación (`MOBILE_SCREENS.md §13`) para pantallas ONLINE-ONLY sin conexión. `S-02` es su
 * primer uso (`PROMPT_FASE_07.md §2.3`: omisión de la tabla de `§13`, no una decisión de diseño).
 */
@Composable
fun BloqueoOnlineOnly(mensaje: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(mensaje, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
