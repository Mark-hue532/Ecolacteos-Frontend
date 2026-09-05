package com.ecolacteos.acopio.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun engineDePlataforma(): HttpClientEngineFactory<*> = Darwin
