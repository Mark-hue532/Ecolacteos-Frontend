package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.data.repository.ResultadoBuscarAnalisisPorFolio

/** `C-05` (`MOBILE_SCREENS.md §6`) -- delgado, distingue "no encontramos ese folio" de un error real. */
class BuscarAnalisisPorFolioUseCase(private val repository: AnalisisCalidadRepository) {
    suspend operator fun invoke(folio: String): ResultadoBuscarAnalisisPorFolio = repository.buscarPorFolio(folio)
}
