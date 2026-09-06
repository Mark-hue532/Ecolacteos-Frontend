package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.CatalogoRepository
import com.ecolacteos.acopio.domain.model.Proveedor

/** Resuelve contra SQLite primero (`§5`, "Escanear QR"), 100% offline real -- ver `CatalogoRepository`. */
class ResolverProveedorPorQrUseCase(private val repository: CatalogoRepository) {
    suspend operator fun invoke(codigoQr: String): Proveedor? = repository.resolverProveedorPorQr(codigoQr)
}
