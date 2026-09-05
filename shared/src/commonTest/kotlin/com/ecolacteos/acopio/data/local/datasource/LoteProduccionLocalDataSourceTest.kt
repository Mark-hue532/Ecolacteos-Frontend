package com.ecolacteos.acopio.data.local.datasource

import com.ecolacteos.acopio.core.Decimal
import com.ecolacteos.acopio.data.local.AcopioDatabase
import com.ecolacteos.acopio.data.local.DispatcherProviderDeTest
import com.ecolacteos.acopio.data.local.crearAcopioDatabase
import com.ecolacteos.acopio.data.local.crearDriverDeTest
import com.ecolacteos.acopio.domain.model.LoteProduccion
import com.ecolacteos.acopio.domain.model.LoteProduccionRegistro
import com.ecolacteos.acopio.domain.model.SyncStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * CRUD de `lote_produccion_local` + `lote_produccion_registro_local` (`PROMPT_FASE_04.md §7`), incluido el
 * `UNIQUE INDEX` que reemplaza al `PRIMARY KEY` con `COALESCE` no-válido de §11.1 (ver checkpoint,
 * "Problemas encontrados").
 */
class LoteProduccionLocalDataSourceTest {

    private fun database(): AcopioDatabase = crearAcopioDatabase(crearDriverDeTest())

    private fun dataSource(database: AcopioDatabase) = LoteProduccionLocalDataSource(
        database.loteProduccionLocalQueries,
        database.loteProduccionRegistroLocalQueries,
        DispatcherProviderDeTest,
    )

    private fun lote(uuidCliente: String = "lote-1") = LoteProduccion(
        uuidCliente = uuidCliente,
        serverId = null,
        usuarioId = "usuario-1",
        fecha = LocalDate(2026, 9, 4),
        tipoQuesoId = "queso-1",
        litrosUsados = Decimal.parseString("500.00"),
        unidadesObtenidas = 40,
        syncStatus = SyncStatus.PENDING,
        syncAttempts = 0,
        syncError = null,
        nextAttemptAt = null,
        creadoEn = LocalDateTime(2026, 9, 4, 6, 0, 0),
        sincronizadoEn = null,
    )

    @Test
    fun `insertar lote hace roundtrip exacto -- incluido unidadesObtenidas como Int`() {
        val ds = dataSource(database())
        val original = lote()

        ds.insertar(original)

        assertEquals(original, ds.obtenerPorUuidCliente("lote-1"))
    }

    @Test
    fun `insertarRegistro acepta padre propio y padre ajeno en el mismo lote`() {
        val ds = dataSource(database())
        ds.insertar(lote())

        ds.insertarRegistro(LoteProduccionRegistro("lote-1", "reg-propio", null))
        ds.insertarRegistro(LoteProduccionRegistro("lote-1", null, "server-ajeno"))

        val registros = ds.obtenerRegistrosPorLote("lote-1")
        assertEquals(2, registros.size)
        assertTrue(registros.any { it.registroAcopioUuidCliente == "reg-propio" })
        assertTrue(registros.any { it.registroAcopioServerId == "server-ajeno" })
    }

    @Test
    fun `UNIQUE INDEX -- el mismo registro de acopio no puede entrar dos veces al mismo lote`() {
        val ds = dataSource(database())
        ds.insertar(lote())
        ds.insertarRegistro(LoteProduccionRegistro("lote-1", "reg-dup", null))

        assertFails { ds.insertarRegistro(LoteProduccionRegistro("lote-1", "reg-dup", null)) }
    }

    /**
     * ⚠️ Corregido tras revisión: `LoteProduccionRegistro` (domain/model) tiene su propio `init {}` que
     * replica esta regla -- `LoteProduccionRegistro("lote-1", null, null)` ya lanza al *construirse*, antes
     * de que `insertarRegistro` llegue a SQLite. Estos dos tests llaman directo a
     * `LoteProduccionRegistroLocalQueries.insertar(...)` (el generado por SQLDelight, bypaseando el modelo
     * de dominio) para probar el `CHECK` real de la tabla, no el `require()` de Kotlin.
     */
    @Test
    fun `CHECK C-02 en SQLite -- ambas referencias nulas falla`() {
        val database = database()
        dataSource(database).insertar(lote())

        assertFails {
            database.loteProduccionRegistroLocalQueries.insertar(
                loteUuidCliente = "lote-1",
                registroAcopioUuidCliente = null,
                registroAcopioServerId = null,
            )
        }
    }

    @Test
    fun `CHECK C-02 en SQLite -- ambas referencias no-nulas tambien falla`() {
        val database = database()
        dataSource(database).insertar(lote())

        assertFails {
            database.loteProduccionRegistroLocalQueries.insertar(
                loteUuidCliente = "lote-1",
                registroAcopioUuidCliente = "reg-propio",
                registroAcopioServerId = "server-ajeno",
            )
        }
    }

    @Test
    fun `eliminarRegistrosPorLote borra solo los del lote indicado`() {
        val ds = dataSource(database())
        ds.insertar(lote("lote-1"))
        ds.insertar(lote("lote-2"))
        ds.insertarRegistro(LoteProduccionRegistro("lote-1", "reg-a", null))
        ds.insertarRegistro(LoteProduccionRegistro("lote-2", "reg-b", null))

        ds.eliminarRegistrosPorLote("lote-1")

        assertEquals(emptyList(), ds.obtenerRegistrosPorLote("lote-1"))
        assertEquals(1, ds.obtenerRegistrosPorLote("lote-2").size)
    }

    @Test
    fun `lote_produccion_registro_local no tiene columna de sync_status -- no compila un campo que no existe`() {
        // Prueba de diseño, no de runtime: LoteProduccionRegistro (domain/model) no declara syncStatus.
        // Si alguien lo agregara sin querer, este archivo dejaría de compilar contra ese constructor.
        val registro = LoteProduccionRegistro(loteUuidCliente = "l", registroAcopioUuidCliente = "r", registroAcopioServerId = null)
        assertEquals("l", registro.loteUuidCliente)
    }
}
