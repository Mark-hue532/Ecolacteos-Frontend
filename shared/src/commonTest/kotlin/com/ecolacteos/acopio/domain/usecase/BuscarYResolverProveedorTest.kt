package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.data.repository.ResultadoResolucionQr
import com.ecolacteos.acopio.domain.model.Proveedor
import com.ecolacteos.acopio.synchronization.responderJson
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun proveedorDePrueba(id: String, nombre: String, codigoQr: String? = null) = Proveedor(
    id = id,
    nombre = nombre,
    zonaActualId = "zona-1",
    zonaActualNombre = "Zona 1",
    codigoQr = codigoQr,
    actualizadoEn = LocalDateTime(2026, 9, 6, 8, 0, 0),
)

/** `A-03`: `BuscarProveedorPorNombreUseCase`, 100% local sobre `proveedor_cache`. */
class BuscarProveedorPorNombreUseCaseTest {

    @Test
    fun `filtra por nombre sin distinguir mayusculas`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.catalogosLocal.reemplazarProveedores(
            listOf(proveedorDePrueba("p1", "Granja El Establo"), proveedorDePrueba("p2", "Lechería Andina")),
        )
        val useCase = BuscarProveedorPorNombreUseCase(fixture.catalogoRepository)

        assertEquals(listOf("Granja El Establo"), useCase("establo").first().map { it.nombre })
    }

    @Test
    fun `query en blanco devuelve el catalogo completo`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.catalogosLocal.reemplazarProveedores(listOf(proveedorDePrueba("p1", "Granja El Establo")))
        val useCase = BuscarProveedorPorNombreUseCase(fixture.catalogoRepository)

        assertEquals(1, useCase("").first().size)
    }

    @Test
    fun `sin coincidencias devuelve lista vacia`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.catalogosLocal.reemplazarProveedores(listOf(proveedorDePrueba("p1", "Granja El Establo")))
        val useCase = BuscarProveedorPorNombreUseCase(fixture.catalogoRepository)

        assertTrue(useCase("no existe").first().isEmpty())
    }
}

/** `A-02`: `ResolverProveedorPorQrUseCase` -- SQLite primero, red como fallback (`MOBILE_ARCHITECTURE.md §3.3`). */
class ResolverProveedorPorQrUseCaseTest {

    // 8: un QR presente en proveedor_cache no dispara ninguna llamada de red.
    @Test
    fun `resuelve contra SQLite primero sin llamar a la red`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        fixture.catalogosLocal.reemplazarProveedores(listOf(proveedorDePrueba("p1", "Granja El Establo", codigoQr = "QR-1")))
        val useCase = ResolverProveedorPorQrUseCase(fixture.catalogoRepository)

        val resultado = useCase("QR-1")

        assertEquals(ResultadoResolucionQr.Encontrado(proveedorDePrueba("p1", "Granja El Establo", codigoQr = "QR-1")), resultado)
        assertTrue(fixture.rutasPedidas.isEmpty())
    }

    // 9a: no está en cache y la red falla -> SinSenalParaConsultar, nunca "no encontrado".
    @Test
    fun `sin cache y sin conexion da sin senal para consultar`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }
        val useCase = ResolverProveedorPorQrUseCase(fixture.catalogoRepository)

        assertEquals(ResultadoResolucionQr.SinSenalParaConsultar, useCase("QR-desconocido"))
    }

    // 9b: no está en cache, la red responde 404 -> NoEncontrado.
    @Test
    fun `sin cache y 404 real da no encontrado`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.NotFound, "no existe") }
        val useCase = ResolverProveedorPorQrUseCase(fixture.catalogoRepository)

        assertEquals(ResultadoResolucionQr.NoEncontrado, useCase("QR-desconocido"))
    }

    // 9c: no está en cache, la red responde 200 -> Encontrado, y queda guardado en cache sin borrar el resto.
    @Test
    fun `sin cache y 200 real encuentra y cachea sin borrar el resto del catalogo`() = runTest {
        val fixture = FixtureRepositorios {
            responderJson("""{"id":"p2","nombre":"Nuevo Proveedor","zonaActualId":"zona-2","zonaActualNombre":"Zona 2","codigoQr":"QR-2"}""")
        }
        fixture.catalogosLocal.reemplazarProveedores(listOf(proveedorDePrueba("p1", "Granja El Establo")))
        val useCase = ResolverProveedorPorQrUseCase(fixture.catalogoRepository)

        val resultado = useCase("QR-2")

        assertTrue(resultado is ResultadoResolucionQr.Encontrado)
        assertEquals("Nuevo Proveedor", resultado.proveedor.nombre)
        val todos = fixture.catalogoRepository.observarProveedores().first()
        assertEquals(setOf("Granja El Establo", "Nuevo Proveedor"), todos.map { it.nombre }.toSet())
    }
}
