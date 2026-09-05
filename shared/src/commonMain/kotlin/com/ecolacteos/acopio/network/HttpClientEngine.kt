package com.ecolacteos.acopio.network

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Engine real de Ktor por plataforma (`PROMPT_FASE_02.md §3`): OkHttp en Android, Darwin en iOS, CIO en
 * JVM (para que `jvm()` tenga un motor disponible -- los tests de esta fase usan `MockEngine`, no este).
 */
internal expect fun engineDePlataforma(): HttpClientEngineFactory<*>
