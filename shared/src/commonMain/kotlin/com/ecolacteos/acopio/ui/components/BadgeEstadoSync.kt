package com.ecolacteos.acopio.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.ui.theme.AcopioColores

/**
 * Badge por fila (`MOBILE_SCREENS.md §10.5`, §13). Un `SYNCED` no lleva badge (no se decora el estado
 * normal) -- este componente no se dibuja en absoluto para ese caso, lo decide el llamador.
 * `PENDING_DEPENDENCY` **nunca** en rojo (`§10.5`, trampa #9): es informativo, no un error.
 */
@Composable
fun BadgeEstadoSync(estado: EstadoSincronizacion, modifier: Modifier = Modifier) {
    val (texto, color) = when (estado) {
        EstadoSincronizacion.Pendiente -> "Pendiente" to AcopioColores.atencion
        is EstadoSincronizacion.EsperandoDependencia -> "Esperando" to AcopioColores.atencion
        EstadoSincronizacion.Sincronizando -> "Sincronizando" to AcopioColores.primario
        EstadoSincronizacion.Sincronizado -> return // sin badge -- el estado normal no se decora
        is EstadoSincronizacion.Fallido -> "Error" to AcopioColores.error
    }

    Surface(color = color.copy(alpha = 0.15f), contentColor = color, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(texto, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
