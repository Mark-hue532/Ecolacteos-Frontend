package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.repository.CorreccionRegistroRepository
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.CorreccionRegistro

/** Online-only (`DATA-004`, `§18.7`, `§5`) -- ver `CorreccionRegistroRepository`, sin cola propia (trampa #9). */
class AnexarCorreccionUseCase(private val repository: CorreccionRegistroRepository) {
    suspend operator fun invoke(
        registroAcopioId: String,
        litrosCorregido: Decimal,
        motivo: String?,
    ): ResultadoDominio<CorreccionRegistro> = repository.anexar(registroAcopioId, litrosCorregido, motivo)
}
