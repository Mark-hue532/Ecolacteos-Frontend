package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.CatalogoRepository
import com.ecolacteos.acopio.data.repository.ResultadoResolucionQr

/** Resuelve contra SQLite primero (`A-02`), con fallback de red -- ver `CatalogoRepository`. */
class ResolverProveedorPorQrUseCase(private val repository: CatalogoRepository) {
    suspend operator fun invoke(codigoQr: String): ResultadoResolucionQr = repository.resolverProveedorPorQr(codigoQr)
}
