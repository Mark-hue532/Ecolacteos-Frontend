package com.ecolacteos.acopio.data.local.adapter

import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.domain.model.Origen
import com.ecolacteos.acopio.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnumColumnAdaptersTest {

    @Test
    fun `enum con contraparte remota cae a UNKNOWN si el valor no matchea`() {
        assertEquals(TipoClienteVenta.UNKNOWN, TipoClienteVentaColumnAdapter.decode("UN_VALOR_FUTURO"))
        assertEquals(TipoClienteVenta.MAYORISTA, TipoClienteVentaColumnAdapter.decode("MAYORISTA"))
    }

    @Test
    fun `Origen tambien cae a UNKNOWN -- fila cacheada por otra version de la app`() {
        assertEquals(Origen.UNKNOWN, OrigenColumnAdapter.decode("ALGO_QUE_NO_EXISTE_TODAVIA"))
        assertEquals(Origen.RESUMEN, OrigenColumnAdapter.decode("RESUMEN"))
    }

    @Test
    fun `SyncStatus NO tiene fallback -- un valor invalido es un bug propio y debe lanzar`() {
        assertFailsWith<IllegalArgumentException> { SyncStatusColumnAdapter.decode("NO_EXISTE") }
        assertEquals(SyncStatus.PENDING, SyncStatusColumnAdapter.decode("PENDING"))
    }
}
