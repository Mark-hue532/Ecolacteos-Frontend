package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.InnovacionRepository
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.AlertaAnomalia

/** `C-07` (`MOBILE_SCREENS.md §6`) -- recibe la `zonaId` ya elegida por la UI; nunca la inventa (trampa #8). */
class ObtenerAlertasPorZonaUseCase(private val repository: InnovacionRepository) {
    suspend operator fun invoke(zonaId: String): ResultadoDominio<List<AlertaAnomalia>> = repository.obtenerAlertasPorZona(zonaId)
}
