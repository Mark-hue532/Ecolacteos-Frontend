package com.ecolacteos.acopio.data.local.datasource

import com.ecolacteos.acopio.data.local.BorradorFormularioQueries
import kotlinx.datetime.LocalDateTime

/** Local Data Source de `borrador_formulario` (`MOBILE_SCREENS.md §3.4`, Fase 7). */
class BorradorFormularioLocalDataSource(private val queries: BorradorFormularioQueries) {

    fun guardar(pantalla: String, payloadJson: String, actualizadoEn: LocalDateTime) {
        queries.guardar(pantalla, payloadJson, actualizadoEn)
    }

    fun obtener(pantalla: String): String? = queries.obtener(pantalla).executeAsOneOrNull()?.payload_json

    fun borrar(pantalla: String) {
        queries.borrar(pantalla)
    }
}
