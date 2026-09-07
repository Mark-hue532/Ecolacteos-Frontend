package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.InnovacionRepository
import com.ecolacteos.acopio.data.repository.ResultadoScoreConfianza

/** `C-08` (`MOBILE_SCREENS.md §6`) -- delgado, distingue el 404 ("sin histórico") en [ResultadoScoreConfianza]. */
class ObtenerScoreConfianzaUseCase(private val repository: InnovacionRepository) {
    suspend operator fun invoke(proveedorId: String): ResultadoScoreConfianza = repository.obtenerScore(proveedorId)
}
