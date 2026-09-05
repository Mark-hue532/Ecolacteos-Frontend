package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.core.DispatcherProvider
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.test.KoinTest
import org.koin.test.check.checkModules
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Este test vale oro (CLAUDE.md/prompt Fase 1): cada fase siguiente agrega dependencias al grafo de Koin,
 * y `checkModules()` avisa al instante si algo quedó sin declarar -- sin él, el error solo aparecería en
 * tiempo de ejecución, en un dispositivo, mucho más tarde.
 */
class CoreModuleTest : KoinTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `el grafo de coreModule se resuelve completo`() {
        koinApplication { modules(coreModule) }.checkModules()
    }

    @Test
    fun `initKoin deja DispatcherProvider inyectable`() {
        initKoin()
        val dispatcherProvider by inject<DispatcherProvider>()
        assertNotNull(dispatcherProvider)
    }

    @Test
    fun `initKoin es seguro de llamar dos veces en el mismo proceso`() {
        // Reproduce lo que pasa si el sistema relanza la Activity/proceso sin matar la VM
        // (se vio en un emulador durante la Fase 1): no debe tirar KoinAppAlreadyStartedException.
        initKoin()
        initKoin()
        val dispatcherProvider by inject<DispatcherProvider>()
        assertNotNull(dispatcherProvider)
    }
}
