package com.ecolacteos.acopio.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Response de los 4 endpoints POST de sync (`MOBILE_DATA_MAPPING.md §5.6`). El **Request** de esos
 * mismos endpoints no es un DTO propio: es un array JSON crudo del DTO de creación correspondiente
 * (`List<RegistroAcopioDTO>`, etc.) -- ver `ApiClient.postLista` (`PROMPT_FASE_02.md §4`), nunca un objeto
 * envolvente `{"items": [...]}`.
 */
@Serializable
data class SyncResultResponse(
    val confirmados: List<String>,
    val errores: List<SyncErrorItem>,
)

@Serializable
data class SyncErrorItem(
    val uuidCliente: String,
    val motivo: String,
)
