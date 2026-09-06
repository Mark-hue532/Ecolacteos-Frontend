package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.data.repository.ComunicadoConfirmacionRepositoryImpl
import com.ecolacteos.acopio.data.repository.NuevaVenta
import com.ecolacteos.acopio.data.repository.VentaRepository
import com.ecolacteos.acopio.domain.ErrorDominio
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.domain.model.Venta
import com.ecolacteos.acopio.network.ApiClient
import com.ecolacteos.acopio.network.ApiConfig
import com.ecolacteos.acopio.network.Entorno
import com.ecolacteos.acopio.network.TokenProviderEnMemoria
import com.ecolacteos.acopio.network.configurarPluginsComunes
import com.ecolacteos.acopio.synchronization.responderJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests 8 y 9 de `PROMPT_FASE_06.md §9`: el `UseCase` como primera línea de defensa de `DATA-010` (`§5`),
 * y el mapeo de errores de un Repository online-only (`§8`).
 */
class ValidacionYPropagacionDeErroresTest {

    /** Repository que revienta si `crear()` llega a llamarse -- prueba negativa de que el `UseCase` corta antes. */
    private class VentaRepositoryQueFalla : VentaRepository {
        override suspend fun crear(datos: NuevaVenta): String =
            error("no debería llamarse -- CrearVentaUseCase tiene que rechazar el tipoCliente inválido antes")
        override fun observarPendientes(): Flow<List<Venta>> = flowOf(emptyList())
        override fun reintentar(uuidCliente: String) = Unit
        override suspend fun purgarSincronizados() = Unit
        override suspend fun obtenerDetalle(uuidCliente: String): com.ecolacteos.acopio.domain.model.VentaDetalle? = null
    }

    private fun datosVenta(tipoCliente: TipoClienteVenta) = NuevaVenta(
        fecha = LocalDate(2026, 9, 6),
        tipoCliente = tipoCliente,
        tipoQuesoId = "queso-1",
        cantidad = 5,
        precioUnitario = Decimal.parseString("25.50"),
    )

    /**
     * `DATA-010`: el backend hace `TipoClienteVenta.valueOf(...)` y devuelve 500, no 400, ante un valor
     * inválido -- el `UseCase` es la primera línea de defensa, no confía en que ese 500 nunca llegue.
     * [TipoClienteVenta.UNKNOWN] es el fallback de deserialización de Fase 2, el único valor "fuera del
     * enum real" que puede llegar hasta acá.
     */
    @Test
    fun `CrearVentaUseCase rechaza tipoCliente fuera del enum antes de llegar al Repository`() = runTest {
        val useCase = CrearVentaUseCase(VentaRepositoryQueFalla())

        val resultado = useCase(datosVenta(TipoClienteVenta.UNKNOWN))

        assertIs<ResultadoCrearVenta.TipoClienteInvalido>(resultado)
    }

    @Test
    fun `CrearVentaUseCase con tipoCliente valido si delega al Repository`() = runTest {
        var vecesLlamado = 0
        val repository = object : VentaRepository {
            override suspend fun crear(datos: NuevaVenta): String {
                vecesLlamado++
                return "uuid-generado"
            }
            override fun observarPendientes(): Flow<List<Venta>> = flowOf(emptyList())
            override fun reintentar(uuidCliente: String) = Unit
            override suspend fun purgarSincronizados() = Unit
            override suspend fun obtenerDetalle(uuidCliente: String): com.ecolacteos.acopio.domain.model.VentaDetalle? = null
        }
        val useCase = CrearVentaUseCase(repository)

        val resultado = useCase(datosVenta(TipoClienteVenta.MAYORISTA))

        val creada = assertIs<ResultadoCrearVenta.Creada>(resultado)
        assertEquals("uuid-generado", creada.uuidCliente)
        assertEquals(1, vecesLlamado)
    }

    /**
     * `§8`: un Repository online-only nunca deja escapar `ApiError` tal cual, ni reintenta por su cuenta un
     * 5xx (trampa #9 -- eso es responsabilidad exclusiva del Sync Engine para los recursos que sí tienen
     * cola, y `ComunicadoConfirmacion` no la tiene, `§11.3`).
     */
    @Test
    fun `ComunicadoConfirmacionRepository propaga un 500 como ErrorDominio Transitorio sin reintentar`() = runTest {
        var vecesLlamado = 0
        val apiConfig = ApiConfig(entorno = Entorno.DEV, baseUrl = "https://api.test")
        val apiClient = ApiClient(
            HttpClient(
                MockEngine { _ ->
                    vecesLlamado++
                    responderJson("""{"mensaje":"error interno"}""", status = HttpStatusCode.InternalServerError)
                },
            ) {
                configurarPluginsComunes(apiConfig, TokenProviderEnMemoria("token-de-prueba"), debug = false)
            },
            apiConfig,
        )
        val repository = ComunicadoConfirmacionRepositoryImpl(apiClient)

        val resultado = repository.confirmar(comunicadoId = "com-1", proveedorId = "prov-1")

        val error = assertIs<ResultadoDominio.Error>(resultado)
        assertIs<ErrorDominio.Transitorio>(error.error)
        assertEquals(1, vecesLlamado, "sin reintento propio -- ver trampa #9 de PROMPT_FASE_06.md")
    }
}
