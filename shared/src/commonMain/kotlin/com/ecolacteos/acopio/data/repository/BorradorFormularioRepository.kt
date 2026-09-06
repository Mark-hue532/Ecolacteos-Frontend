package com.ecolacteos.acopio.data.repository

import com.ecolacteos.acopio.core.ahoraComoFechaHora
import com.ecolacteos.acopio.data.local.datasource.BorradorFormularioLocalDataSource
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Borradores de formulario (`MOBILE_SCREENS.md §3.4`, `PROMPT_FASE_07.md §2.5`). `payloadJson` es opaco a
 * propósito -- cada `ViewModel` decide su propia forma de borrador (`BorradorVenta` en `V-02`, etc.); este
 * Repository no conoce ningún formulario en particular, solo persiste/lee/borra por clave de `pantalla`.
 */
interface BorradorFormularioRepository {
    fun guardar(pantalla: String, payloadJson: String)
    fun obtener(pantalla: String): String?
    fun descartar(pantalla: String)
}

class BorradorFormularioRepositoryImpl(
    private val local: BorradorFormularioLocalDataSource,
    private val reloj: Clock = Clock.System,
    private val zona: TimeZone = TimeZone.currentSystemDefault(),
) : BorradorFormularioRepository {
    override fun guardar(pantalla: String, payloadJson: String) {
        local.guardar(pantalla, payloadJson, ahoraComoFechaHora(reloj, zona))
    }

    override fun obtener(pantalla: String): String? = local.obtener(pantalla)

    override fun descartar(pantalla: String) {
        local.borrar(pantalla)
    }
}
