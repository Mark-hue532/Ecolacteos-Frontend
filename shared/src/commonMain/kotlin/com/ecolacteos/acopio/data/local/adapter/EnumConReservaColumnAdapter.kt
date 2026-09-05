package com.ecolacteos.acopio.data.local.adapter

import app.cash.sqldelight.ColumnAdapter
import com.ecolacteos.acopio.data.remote.dto.CicloCapital
import com.ecolacteos.acopio.data.remote.dto.TipoClienteVenta
import com.ecolacteos.acopio.domain.model.Origen

/**
 * Base reusable para enums con contraparte remota (`TipoClienteVenta`, `CicloCapital`, [Origen] de
 * `registro_acopio_cache` -- `PROMPT_FASE_04.md §3`): mismo criterio defensivo que
 * `EnumConReservaSerializer` de Fase 2 (`data/remote/serializer/`), ahora del lado de SQLite -- una fila
 * cacheada puede haber sido escrita por una versión anterior/posterior de la app con un valor de enum que
 * esta versión no reconoce, y eso no puede romper la lectura completa de la tabla. Nunca lanza; cae a
 * [reserva].
 */
abstract class EnumConReservaColumnAdapter<T : Enum<T>>(
    private val valores: Array<T>,
    private val reserva: T,
) : ColumnAdapter<T, String> {

    override fun decode(databaseValue: String): T =
        valores.firstOrNull { it.name == databaseValue } ?: reserva

    override fun encode(value: T): String = value.name
}

object TipoClienteVentaColumnAdapter :
    EnumConReservaColumnAdapter<TipoClienteVenta>(TipoClienteVenta.entries.toTypedArray(), TipoClienteVenta.UNKNOWN)

object CicloCapitalColumnAdapter :
    EnumConReservaColumnAdapter<CicloCapital>(CicloCapital.entries.toTypedArray(), CicloCapital.UNKNOWN)

object OrigenColumnAdapter :
    EnumConReservaColumnAdapter<Origen>(Origen.entries.toTypedArray(), Origen.UNKNOWN)
