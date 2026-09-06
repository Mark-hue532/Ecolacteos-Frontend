package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.repository.NuevoAnalisisCalidad
import com.ecolacteos.acopio.data.repository.NuevoRegistroAcopio
import com.ecolacteos.acopio.data.repository.ReferenciaRegistroAcopio
import com.ecolacteos.acopio.data.repository.ResultadoCrearHijo
import com.ecolacteos.acopio.domain.model.EstadoSincronizacion
import com.ecolacteos.acopio.domain.model.SyncStatus
import com.ecolacteos.acopio.network.Endpoints
import com.ecolacteos.acopio.synchronization.ResultadoCiclo
import com.ecolacteos.acopio.synchronization.cuerpoCambiosVacio
import com.ecolacteos.acopio.synchronization.cuerpoSync
import com.ecolacteos.acopio.synchronization.responderJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests 3, 4 y 5 de `PROMPT_FASE_06.md §9`: los tres caminos de la máquina de decisión de
 * `ResolutorPadreRegistroAcopio` (`§4.2`), vistos a través de la capa pública -- Repository/UseCase --
 * porque es lo que esta fase promete exponer, no el mecanismo interno (ya cubierto a nivel `SyncEngine` en
 * `SyncEngineTest` de Fase 5).
 */
class ResolucionDePadreTest {

    private fun datosAnalisis(referencia: ReferenciaRegistroAcopio) = NuevoAnalisisCalidad(
        referenciaPadre = referencia,
        folioMuestra = "F-001",
        agua = Decimal.parseString("3.20"),
        proteina = null,
        lactosa = null,
        densidad = null,
        temperatura = null,
        ph = null,
        aguaAnadida = false,
    )

    /**
     * El caso real de hoy (`DATA-014`): el padre sincroniza SIN `server_id`, así que el hijo no puede
     * promoverse nunca solo. La capa de dominio tiene que exponer eso como [EstadoSincronizacion.EsperandoDependencia]
     * con [EstadoSincronizacion.EsperandoDependencia.motivoConocido] no nulo -- nunca como
     * [EstadoSincronizacion.Pendiente] genérico, y nunca resuelto (§7 de la fase, literal).
     */
    @Test
    fun `padre e hijo mismo dispositivo -- tras el ciclo el hijo es EsperandoDependencia con motivo y nunca Pendiente`() = runTest {
        lateinit var uuidPadre: String
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.SYNC_REGISTROS_ACOPIO) {
                responderJson(cuerpoSync(confirmados = listOf(uuidPadre)))
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val crearRegistro = CrearRegistroAcopioUseCase(fixture.registroAcopioRepository)
        val crearAnalisis = CrearAnalisisCalidadUseCase(fixture.analisisCalidadRepository)
        val observarPendientes = ObservarPendientesUseCase(
            fixture.registroAcopioRepository,
            fixture.analisisCalidadRepository,
            fixture.loteProduccionRepository,
            fixture.ventaRepository,
        )

        uuidPadre = crearRegistro(
            NuevoRegistroAcopio(
                proveedorId = "prov-1",
                unidadId = "unidad-1",
                fechaHora = LocalDateTime(2026, 9, 6, 6, 0, 0),
                litros = Decimal.parseString("120.50"),
                gpsLat = null,
                gpsLng = null,
                motivoObservacionId = null,
                litrosPorVoz = false,
            ),
        )
        val resultadoHijo = crearAnalisis(datosAnalisis(ReferenciaRegistroAcopio.Propio(uuidPadre)))
        assertIs<ResultadoCrearHijo.Creado>(resultadoHijo)

        // Antes de cualquier ciclo: retenido a propósito, todavía sin sync_error (§7: ventana breve).
        val antes = fixture.analisisCalidadRepository.observarPendientes().first().single()
        assertEquals(SyncStatus.PENDING_DEPENDENCY, antes.syncStatus)

        val resultadoCiclo = fixture.syncEngine.ejecutarCiclo()
        assertIs<ResultadoCiclo.Completado>(resultadoCiclo)

        val resumen = observarPendientes().first()
        val estadoHijo = resumen.analisis.single().estado
        val esperando = assertIs<EstadoSincronizacion.EsperandoDependencia>(estadoHijo)
        assertNotNull(esperando.motivoConocido)
        assertTrue(esperando.motivoConocido.contains("DATA-014"))
    }

    /**
     * Mecanismo 2 de `§18.1`: un padre ajeno recién descubierto vía `ObtenerRegistrosDeProveedorUseCase`
     * (población on-demand, `§4.2` caso 3) resuelve directo -- el hijo nace `PENDING`, nunca pasa por
     * `PENDING_DEPENDENCY`.
     */
    @Test
    fun `padre ajeno no cacheado -- se puebla con ObtenerRegistrosDeProveedorUseCase y el hijo nace ya resuelto`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registrosAcopioPorProveedor("prov-ajeno")) {
                responderJson(
                    """[{"id":"srv-ajeno-1","fechaHora":"2026-09-05T06:00:00","litros":80.00,"tieneObservacion":false}]""",
                )
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val obtenerRegistros = ObtenerRegistrosDeProveedorUseCase(fixture.registroAcopioRepository)
        val crearAnalisis = CrearAnalisisCalidadUseCase(fixture.analisisCalidadRepository)

        val referencias = obtenerRegistros("prov-ajeno")
        assertEquals("srv-ajeno-1", referencias.single().id)

        val resultado = crearAnalisis(datosAnalisis(ReferenciaRegistroAcopio.Ajeno("srv-ajeno-1")))
        assertIs<ResultadoCrearHijo.Creado>(resultado)

        val hijo = fixture.analisisCalidadRepository.observarPendientes().first().single()
        assertEquals(SyncStatus.PENDING, hijo.syncStatus, "resuelto de una -- nunca PENDING_DEPENDENCY")
        assertEquals("srv-ajeno-1", hijo.registroAcopioServerId)
        assertNull(hijo.registroAcopioUuidCliente)
    }

    /**
     * `§4.2.a` -- "el caso que no cubre ninguna de las dos" de `§18.1`: un padre ajeno que ni siquiera está
     * cacheado, y sin conectividad para ir a buscarlo. No hay ninguna referencia que permita diferirlo con
     * seguridad, así que la creación se rechaza entera -- nunca un estado que sugiera que se va a resolver
     * solo.
     */
    @Test
    fun `padre ajeno no cacheado y sin conectividad -- error de dominio explicito y no un pendiente enganoso`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registroAcopioPorId("srv-no-cacheado")) {
                throw RuntimeException("sin conectividad simulada")
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val crearAnalisis = CrearAnalisisCalidadUseCase(fixture.analisisCalidadRepository)

        val resultado = crearAnalisis(datosAnalisis(ReferenciaRegistroAcopio.Ajeno("srv-no-cacheado")))

        assertIs<ResultadoCrearHijo.PadreAjenoNoResolubleSinConectividad>(resultado)
        assertEquals(emptyList(), fixture.analisisCalidadRepository.observarPendientes().first(), "no se escribe nada")
    }

    /** Variante de la misma trampa con un 500 en vez de una excepción de red -- mismo resultado esperado. */
    @Test
    fun `padre ajeno no cacheado con 500 del servidor -- mismo error de dominio y no un 5xx crudo`() = runTest {
        val fixture = FixtureRepositorios { request ->
            if (request.url.encodedPath == Endpoints.registroAcopioPorId("srv-caido")) {
                responderJson("""{"mensaje":"error interno"}""", status = HttpStatusCode.InternalServerError)
            } else {
                responderJson(cuerpoCambiosVacio())
            }
        }
        val crearAnalisis = CrearAnalisisCalidadUseCase(fixture.analisisCalidadRepository)

        val resultado = crearAnalisis(datosAnalisis(ReferenciaRegistroAcopio.Ajeno("srv-caido")))

        assertIs<ResultadoCrearHijo.PadreAjenoNoResolubleSinConectividad>(resultado)
    }
}
