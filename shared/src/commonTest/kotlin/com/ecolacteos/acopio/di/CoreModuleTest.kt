package com.ecolacteos.acopio.di

import app.cash.sqldelight.db.SqlDriver
import com.ecolacteos.acopio.core.DispatcherProvider
import com.ecolacteos.acopio.data.local.crearDriverDeTest
import com.ecolacteos.acopio.domain.GestorSesion
import com.ecolacteos.acopio.domain.VerificadorPendientes
import com.ecolacteos.acopio.network.TokenProvider
import com.ecolacteos.acopio.security.AlmacenamientoSeguroDeSesion
import com.ecolacteos.acopio.security.AlmacenamientoSeguroDeSesionFake
import com.ecolacteos.acopio.synchronization.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
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
        koinApplication {
            modules(coreModule)
        }.checkModules()
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

    /**
     * `securityModule` (Fase 3) no declara el binding de `AlmacenamientoSeguroDeSesion` -- cada plataforma
     * lo hace en el suyo (`androidApp/MainActivity.kt`, `di/IosSecurityModule.kt`), ver el comentario en
     * `SecurityModule.kt`. Acá se lo damos con un fake -- lo mismo que hace `GestorSesionTest`, apto para
     * JVM -- para poder correr `checkModules()` sobre el grafo completo (criterio de aceptación §7).
     *
     * Este slice deliberadamente **no** incluye `localModule`/`useCaseModule` (Fase 6) -- por eso
     * `VerificadorPendientes` (que `GestorSesionImpl` necesita) se fakea acá directo, en vez de arrastrar
     * toda la cadena Repository→LocalDataSource→SqlDriver de `VerificarPendientesUseCase`.
     */
    @Test
    fun `el grafo completo -- core + network + security -- se resuelve con un almacenamiento fake`() {
        koinApplication {
            modules(
                coreModule,
                networkModule,
                securityModule,
                module {
                    single<AlmacenamientoSeguroDeSesion> {
                        AlmacenamientoSeguroDeSesionFake()
                    }

                    single<VerificadorPendientes> {
                        VerificadorPendientesFakeSinTrabajo
                    }
                },
            )
        }.checkModules()
    }

    /**
     * `initKoin()` real (Fase 6 en adelante) wirea también `localModule`/`syncModule`/`repositoryModule`/
     * `useCaseModule` -- `GestorSesionImpl` pide `VerificadorPendientes`, que ahora resuelve
     * `VerificarPendientesUseCase` (`useCaseModule`), que a su vez necesita los 4 `Repository` de escritura
     * y por lo tanto `SqlDriver`/`ConnectivityObserver`. Usamos fakes agnósticos para no depender de Context de Android.
     */
    @Test
    fun `initKoin con el almacenamiento fake deja GestorSesion y TokenProvider inyectables`() {
        initKoin {
            modules(
                module {
                    single<AlmacenamientoSeguroDeSesion> {
                        AlmacenamientoSeguroDeSesionFake()
                    }

                    single<SqlDriver> {
                        crearDriverDeTest()
                    }

                    single<ConnectivityObserver> {
                        ConnectivityObserverFake
                    }
                },
            )
        }

        val gestorSesion by inject<GestorSesion>()
        val tokenProvider by inject<TokenProvider>()

        assertNotNull(gestorSesion)
        assertNotNull(tokenProvider)
    }

    private companion object {
        val VerificadorPendientesFakeSinTrabajo = object : VerificadorPendientes {
            override suspend fun hayTrabajoSinSincronizar(): Boolean = false
        }
    }
}

object ConnectivityObserverFake : ConnectivityObserver {
    override val conectado: kotlinx.coroutines.flow.Flow<Boolean> =
        MutableStateFlow(true)
}
