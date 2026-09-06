package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.repository.NuevoRegistroAcopio
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.synchronization.GestorSesionFake
import com.ecolacteos.acopio.synchronization.ResultadoCiclo
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.responderJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests 6 y 7 de `PROMPT_FASE_06.md §9`: el escenario multiusuario completo (`§6` punto 3, `MOBILE_ARCHITECTURE.md
 * §4` "Multiusuario") y la política de logout de esa misma sección.
 */
class MultiusuarioYLogoutTest {

    private fun datosRegistro() = NuevoRegistroAcopio(
        proveedorId = "prov-1",
        unidadId = "unidad-1",
        fechaHora = LocalDateTime(2026, 9, 6, 6, 0, 0),
        litros = Decimal.parseString("120.50"),
        gpsLat = null,
        gpsLng = null,
        motivoObservacionId = null,
        litrosPorVoz = false,
    )

    /**
     * `§6` punto 3, literal: "usuario A con una fila PENDING, usuario B loguea en el mismo dispositivo --
     * `VerificarPendientesUseCase` para la sesión de B debe dar 0, y un ciclo de sync con la sesión de B
     * activa no debe subir la fila de A". Cubre de paso la trampa #6 (Fase 5 ya filtraba por `usuario_id`
     * en `obtenerPendientes`, pero es esta fase la que lo verifica desde la capa pública).
     */
    @Test
    fun `escenario multiusuario -- usuario B no ve ni sincroniza los pendientes de usuario A`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        val crearRegistro = CrearRegistroAcopioUseCase(fixture.registroAcopioRepository)

        // Usuario A (sesión por defecto del fixture) crea un registro -- queda PENDING.
        val uuidDeA = crearRegistro(datosRegistro())
        assertEquals(SyncStatus.PENDING, fixture.registrosLocal.obtenerPorUuidCliente(uuidDeA)?.syncStatus)

        // Usuario B loguea en el mismo dispositivo.
        fixture.gestorSesion.loguearComo(GestorSesionFake.SESION_DE_PRUEBA_B)

        val verificarPendientesDeB = VerificarPendientesUseCase(
            fixture.registroAcopioRepository,
            fixture.analisisCalidadRepository,
            fixture.loteProduccionRepository,
            fixture.ventaRepository,
        )
        assertEquals(0, verificarPendientesDeB.contar().total, "B no debe ver los pendientes de A")
        assertEquals(emptyList(), fixture.registroAcopioRepository.observarPendientes().first())

        // Un ciclo con B activo no debe ni intentar subir la fila de A.
        val resultado = fixture.syncEngine.ejecutarCiclo()
        assertIs<ResultadoCiclo.Completado>(resultado)
        assertEquals(0, fixture.cuantasVecesSePidio(Endpoints.SYNC_REGISTROS_ACOPIO))
        assertEquals(SyncStatus.PENDING, fixture.registrosLocal.obtenerPorUuidCliente(uuidDeA)?.syncStatus, "fila de A intacta")
        assertEquals(GestorSesionFake.USUARIO_ID, fixture.registrosLocal.obtenerPorUuidCliente(uuidDeA)?.usuarioId)
    }

    @Test
    fun `logout bloqueado -- con pendientes propios no borra nada y devuelve el conteo`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        val crearRegistro = CrearRegistroAcopioUseCase(fixture.registroAcopioRepository)
        val uuidCliente = crearRegistro(datosRegistro())

        val resultado = fixture.logout()

        val bloqueada = assertIs<ResultadoLogout.BloqueadaPorPendientes>(resultado)
        assertEquals(1, bloqueada.conteo.total)
        assertNotNull(fixture.gestorSesion.sesion.value, "la sesión no se invalida si queda bloqueada")
        assertEquals(SyncStatus.PENDING, fixture.registrosLocal.obtenerPorUuidCliente(uuidCliente)?.syncStatus)
    }

    @Test
    fun `logout limpio -- sin pendientes borra token caches personales y el historial ya sincronizado`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }

        // Semillas: un SYNCED propio (debe purgarse) y datos personales en cache (deben borrarse siempre).
        fixture.registrosLocal.insertar(registroSincronizado("ya-sincronizado"))
        fixture.catalogosLocal.reemplazarProveedores(listOf(proveedorDePrueba()))

        val resultado = fixture.logout()

        assertIs<ResultadoLogout.Cerrada>(resultado)
        assertNull(fixture.gestorSesion.sesion.value, "logout limpio invalida la sesión")
        assertNull(fixture.registrosLocal.obtenerPorUuidCliente("ya-sincronizado"), "el historial SYNCED se purga")
        assertEquals(emptyList(), fixture.catalogosLocal.observarProveedores().first(), "RNF-12: cache personal borrada")
    }

    @Test
    fun `logout conservando datos -- borra token y caches personales pero nunca las filas locales pendientes`() = runTest {
        val fixture = FixtureRepositorios { responderJson(cuerpoCambiosVacio()) }
        val crearRegistro = CrearRegistroAcopioUseCase(fixture.registroAcopioRepository)
        val uuidCliente = crearRegistro(datosRegistro())
        fixture.catalogosLocal.reemplazarProveedores(listOf(proveedorDePrueba()))

        val resultado = fixture.logout(conservarDatos = true)

        assertIs<ResultadoLogout.Cerrada>(resultado)
        assertNull(fixture.gestorSesion.sesion.value, "el token se borra igual")
        assertEquals(
            SyncStatus.PENDING,
            fixture.registrosLocal.obtenerPorUuidCliente(uuidCliente)?.syncStatus,
            "CLAUDE.md §3.6 -- el trabajo no confirmado nunca se borra, ni con logout explícito",
        )
        assertEquals(emptyList(), fixture.catalogosLocal.observarProveedores().first(), "RNF-12 aplica igual")
    }

    private fun registroSincronizado(uuidCliente: String) = RegistroAcopio(
        uuidCliente = uuidCliente,
        serverId = "srv-1",
        usuarioId = GestorSesionFake.USUARIO_ID,
        proveedorId = "prov-1",
        unidadId = "unidad-1",
        fechaHora = LocalDateTime(2026, 9, 5, 6, 0, 0),
        litros = Decimal.parseString("100.00"),
        gpsLat = null,
        gpsLng = null,
        motivoObservacionId = null,
        litrosPorVoz = false,
        syncStatus = SyncStatus.SYNCED,
        syncAttempts = 0,
        syncError = null,
        nextAttemptAt = null,
        creadoEn = LocalDateTime(2026, 9, 5, 6, 0, 1),
        sincronizadoEn = LocalDateTime(2026, 9, 5, 6, 5, 0),
    )

    private fun proveedorDePrueba() = Proveedor(
        id = "prov-1",
        nombre = "Fundo Los Andes",
        zonaActualId = "zona-1",
        zonaActualNombre = "Zona Norte",
        codigoQr = "QR-1",
        actualizadoEn = LocalDateTime(2026, 9, 5, 6, 0, 0),
    )
}
