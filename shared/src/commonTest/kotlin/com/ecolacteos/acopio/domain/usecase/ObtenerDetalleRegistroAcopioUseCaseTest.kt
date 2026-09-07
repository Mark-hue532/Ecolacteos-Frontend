package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.core.aTextoConEscala
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.RegistroAcopio
import com.ecolacteos.acopio.domain.model.RegistroAcopioReferencia
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.synchronization.GestorSesionFake
import com.ecolacteos.acopio.synchronization.responderJson
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** `A-06`: `ObtenerDetalleRegistroAcopioUseCase` -- ONLINE+CACHE con tres niveles de degradación. */
class ObtenerDetalleRegistroAcopioUseCaseTest {

    // 12 (§5 del prompt): fechaHora/sincronizadoEn son campos separados, ninguno una duracion entre ambos.
    @Test
    fun `detalle completo trae motivoObservacionTexto y sincronizadoEn del servidor`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson(
                """{"id":"srv-1","uuidCliente":"uuid-x","proveedorId":"prov-1","proveedorNombre":"Granja El Establo",
                   |"unidadId":"unidad-1","fechaHora":"2026-09-06T08:00:00","litros":120.50,
                   |"motivoObservacion":"Leche aguada","litrosPorVoz":false,"sincronizadoEn":"2026-09-06T09:00:00"}
                """.trimMargin(),
            )
        }
        val useCase = ObtenerDetalleRegistroAcopioUseCase(fixture.registroAcopioRepository)

        val detalle = useCase("srv-1")

        assertNotNull(detalle)
        assertEquals("Leche aguada", detalle.motivoObservacionTexto)
        assertNull(detalle.motivoObservacionId) // NAME_MISMATCH -- el Response solo trae el texto
        assertEquals(LocalDateTime(2026, 9, 6, 8, 0, 0), detalle.fechaHora)
        assertEquals(LocalDateTime(2026, 9, 6, 9, 0, 0), detalle.sincronizadoEn)

        // Oportunista: la cache queda enriquecida con origen DETALLE para una próxima consulta offline.
        assertNotNull(fixture.cacheLocal.obtenerPorServerId("srv-1"))
    }

    @Test
    fun `sin red pero con fila en cache ajena degrada a un detalle parcial`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.cacheLocal.upsert(
            RegistroAcopioReferencia(
                id = "srv-2",
                uuidCliente = null,
                proveedorId = "prov-2",
                proveedorNombre = "Lechería Andina",
                fechaHora = LocalDateTime(2026, 9, 5, 7, 0, 0),
                litros = Decimal.parseString("80.00"),
                tieneObservacion = false,
                origen = Origen.RESUMEN,
                actualizadoEn = LocalDateTime(2026, 9, 5, 7, 5, 0),
            ),
        )
        val useCase = ObtenerDetalleRegistroAcopioUseCase(fixture.registroAcopioRepository)

        val detalle = useCase("srv-2")

        assertNotNull(detalle)
        assertEquals("Lechería Andina", detalle.proveedorNombre)
        assertNull(detalle.gpsLat)
        assertNull(detalle.motivoObservacionTexto)
        assertNull(detalle.sincronizadoEn)
    }

    @Test
    fun `sin red y sin cache ajena pero con fila local propia degrada al registro local`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.registrosLocal.insertar(
            RegistroAcopio(
                uuidCliente = "uuid-propio",
                serverId = "srv-3",
                usuarioId = GestorSesionFake.USUARIO_ID,
                proveedorId = "prov-3",
                unidadId = "unidad-1",
                fechaHora = LocalDateTime(2026, 9, 6, 6, 0, 0),
                litros = Decimal.parseString("50.00"),
                gpsLat = Decimal.parseString("-12.045678"),
                gpsLng = Decimal.parseString("-77.030348"),
                motivoObservacionId = "motivo-1",
                litrosPorVoz = false,
                syncStatus = SyncStatus.SYNCED,
                syncAttempts = 0,
                syncError = null,
                nextAttemptAt = null,
                creadoEn = LocalDateTime(2026, 9, 6, 6, 0, 1),
                sincronizadoEn = LocalDateTime(2026, 9, 6, 6, 5, 0),
            ),
        )
        val useCase = ObtenerDetalleRegistroAcopioUseCase(fixture.registroAcopioRepository)

        val detalle = useCase("srv-3")

        assertNotNull(detalle)
        assertEquals("-12.045678", detalle.gpsLat?.aTextoConEscala(6))
        assertEquals("motivo-1", detalle.motivoObservacionId)
        assertNull(detalle.motivoObservacionTexto) // el fallback local nunca inventa la descripción
        assertEquals(LocalDateTime(2026, 9, 6, 6, 5, 0), detalle.sincronizadoEn)
    }

    @Test
    fun `sin red y sin ninguna fuente local devuelve null`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val useCase = ObtenerDetalleRegistroAcopioUseCase(fixture.registroAcopioRepository)

        assertNull(useCase("srv-inexistente"))
    }
}
