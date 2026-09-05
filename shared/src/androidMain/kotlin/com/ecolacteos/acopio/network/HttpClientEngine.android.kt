package com.ecolacteos.acopio.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun engineDePlataforma(): HttpClientEngineFactory<*> = OkHttp
