package com.ecolacteos.acopio.domain.usecase

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.repository.NuevaRecepcionPlanta
import com.ecolacteos.acopio.data.repository.ResultadoRegistrarRecepcion
import com.ecolacteos.acopio.domain.ResultadoDominio
import com.ecolacteos.acopio.network.Endpoints
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Punto 15 de `PROMPT_FASE_08E.md §8`: los 3 Repository nuevos (`RecepcionPlantaRepository`, `PagoRepository`,
 * `InnovacionRepository`) son ONLINE-ONLY sin tabla local (`§11.3` -- ninguno de los tres recibe un
 * `LocalDataSource` en su constructor, así que "ninguna tabla `*_local` cambió" es estructural, no algo que
 * dependa del resultado de la llamada). Lo que sí hay que probar es que un fallo de red sube como
 * [ResultadoDominio.Error]/[ResultadoRegistrarRecepcion.Error] -- nunca se traga en silencio -- y que no hay
 * reintento automático (una sola request por llamada, mismo criterio que `CorreccionRegistroRepository`,
 * `PROMPT_FASE_06.md §10` trampa 9).
 */
class RepositoriosOnlineOnlyFase8ETest {

    @Test
    fun `RecepcionPlantaRepository no encola ni reintenta tras un fallo de red`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }

        val resultado = fixture.recepcionPlantaRepository.registrar(
            NuevaRecepcionPlanta(
                fecha = LocalDate(2026, 9, 6),
                turno = null,
                unidadId = "unidad-1",
                litrosCampo = Decimal.parseString("100.00"),
                litrosPlanta = Decimal.parseString("98.00"),
            ),
        )

        assertTrue(resultado is ResultadoRegistrarRecepcion.Error, "el fallo sube como resultado de dominio, no se traga en silencio")
        assertEquals(1, fixture.cuantasVecesSePidio(Endpoints.RECEPCION_PLANTA), "sin reintento automatico")
    }

    @Test
    fun `PagoRepository no encola ni reintenta tras un fallo de red`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }

        val resultado = fixture.pagoRepository.obtenerPorProveedor("prov-1")

        assertTrue(resultado is ResultadoDominio.Error)
        assertEquals(1, fixture.cuantasVecesSePidio(Endpoints.pagosPorProveedor("prov-1")), "sin reintento automatico")
    }

    @Test
    fun `InnovacionRepository no encola ni reintenta tras un fallo de red`() = runTest {
        val fixture = FixtureRepositorios { respondError(HttpStatusCode.ServiceUnavailable) }

        val resultado = fixture.innovacionRepository.obtenerAlertasPorZona("zona-1")

        assertTrue(resultado is ResultadoDominio.Error)
        assertEquals(1, fixture.cuantasVecesSePidio(Endpoints.ALERTAS), "sin reintento automatico")
    }
}
