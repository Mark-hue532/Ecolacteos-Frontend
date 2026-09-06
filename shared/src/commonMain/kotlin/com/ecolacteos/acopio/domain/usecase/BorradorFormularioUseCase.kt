package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.BorradorFormularioRepository

/**
 * `MOBILE_SCREENS.md §3.4` a través de la capa de `UseCase` (`CLAUDE.md §3.4`) -- agrupa las 3 operaciones
 * igual que [ObservarCatalogosUseCase], en vez de tres clases de una línea cada una.
 */
class BorradorFormularioUseCase(private val repository: BorradorFormularioRepository) {
    fun guardar(pantalla: String, payloadJson: String) = repository.guardar(pantalla, payloadJson)
    fun obtener(pantalla: String): String? = repository.obtener(pantalla)
    fun descartar(pantalla: String) = repository.descartar(pantalla)
}
