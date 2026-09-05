package com.ecolacteos.acopio.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun engineDePlataforma(): HttpClientEngineFactory<*> = CIO
