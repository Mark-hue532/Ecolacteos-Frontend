package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.domain.model.Unidad
import com.ecolacteos.acopio.synchronization.GestorSesionFake
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun unidadDePrueba(id: String, responsableId: String, zonaId: String?) = Unidad(
    id = id,
    placa = "ABC-$id",
    capacidadTon = null,
    zonaId = zonaId,
    responsableId = responsableId,
    responsableNombre = "Responsable",
    actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
)

/** `A-01`: `ObtenerZonaAsignadaUseCase` (`DATA-016`) y `ObservarProveedoresVisitadosHoyUseCase`. */
class RutaYZonaTest {

    // 1 (`§7` decisión #2 / DATA-016): una sola Unidad con responsableId propio -> resuelve esa zona.
    @Test
    fun `resuelve la zona cuando hay exactamente una unidad propia con zonaId`() = runTestFixture {
        catalogosLocal.reemplazarUnidades(listOf(unidadDePrueba("u1", GestorSesionFake.USUARIO_ID, "zona-1")))
        val useCase = ObtenerZonaAsignadaUseCase(catalogoRepository, gestorSesion)

        assertEquals("zona-1", useCase())
    }

    // 2: sin ninguna unidad propia -> null, nunca una zona inventada.
    @Test
    fun `sin unidades propias devuelve null`() = runTestFixture {
        catalogosLocal.reemplazarUnidades(listOf(unidadDePrueba("u1", "otro-usuario", "zona-1")))
        val useCase = ObtenerZonaAsignadaUseCase(catalogoRepository, gestorSesion)

        assertNull(useCase())
    }

    // 3: dos unidades propias con zonas DISTINTAS -> ambigüedad real, null (nunca elegir una al azar).
    @Test
    fun `dos unidades propias con zonas distintas es ambiguo -- null`() = runTestFixture {
        catalogosLocal.reemplazarUnidades(
            listOf(
                unidadDePrueba("u1", GestorSesionFake.USUARIO_ID, "zona-1"),
                unidadDePrueba("u2", GestorSesionFake.USUARIO_ID, "zona-2"),
            ),
        )
        val useCase = ObtenerZonaAsignadaUseCase(catalogoRepository, gestorSesion)

        assertNull(useCase())
    }

    // 4: dos unidades propias con la MISMA zona -> no es ambiguo, se resuelve igual.
    @Test
    fun `dos unidades propias con la misma zona no es ambiguo`() = runTestFixture {
        catalogosLocal.reemplazarUnidades(
            listOf(
                unidadDePrueba("u1", GestorSesionFake.USUARIO_ID, "zona-1"),
                unidadDePrueba("u2", GestorSesionFake.USUARIO_ID, "zona-1"),
            ),
        )
        val useCase = ObtenerZonaAsignadaUseCase(catalogoRepository, gestorSesion)

        assertEquals("zona-1", useCase())
    }

    // 5 (A-01, "ya visitado hoy"): un registro de hoy marca a su proveedor como visitado.
    @Test
    fun `un registro de hoy marca al proveedor como visitado`() = runTestFixture {
        registrosLocal.insertar(registroDePrueba(fecha = LocalDateTime(2026, 9, 6, 9, 0, 0)))
        val useCase = ObservarProveedoresVisitadosHoyUseCase(registroAcopioRepository, reloj, TimeZone.UTC)

        assertEquals(setOf("prov-1"), useCase().first())
    }

    // 6: un registro de OTRO día no cuenta como visitado hoy.
    @Test
    fun `un registro de otro dia no marca visitado hoy`() = runTestFixture {
        registrosLocal.insertar(registroDePrueba(fecha = LocalDateTime(2026, 9, 5, 9, 0, 0)))
        val useCase = ObservarProveedoresVisitadosHoyUseCase(registroAcopioRepository, reloj, TimeZone.UTC)

        assertEquals(emptySet(), useCase().first())
    }
}

private fun registroDePrueba(fecha: LocalDateTime) = RegistroAcopio(
    uuidCliente = "uuid-1",
    serverId = null,
    usuarioId = GestorSesionFake.USUARIO_ID,
    proveedorId = "prov-1",
    unidadId = "unidad-1",
    fechaHora = fecha,
    litros = Decimal.parseString("100.00"),
    gpsLat = null,
    gpsLng = null,
    motivoObservacionId = null,
    litrosPorVoz = false,
    syncStatus = SyncStatus.PENDING,
    syncAttempts = 0,
    syncError = null,
    nextAttemptAt = null,
    creadoEn = fecha,
    sincronizadoEn = null,
)

/** El reloj de `FixtureRepositorios` está fijo en `2026-09-06T12:00:00Z` -- "hoy" es el 6/9. */
private fun runTestFixture(bloque: suspend FixtureRepositorios.() -> Unit) = runTest {
    val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
    fixture.bloque()
}
