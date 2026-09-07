package com.ecolacteos.acopio.plataforma

import kotlinx.coroutines.awaitCancellation

/**
 * Fake de [GestorPermisos] (`PROMPT_FASE_08A.md §3.1`): permite conceder/revocar sin tocar nada de
 * plataforma. Por defecto, ningún permiso está concedido -- igual que un dispositivo recién instalado.
 */
class GestorPermisosFake(vararg concedidosIniciales: Permiso) : GestorPermisos {
    private val concedidos = concedidosIniciales.toMutableSet()

    var vecesAbrioAjustes = 0
        private set

    override fun tieneConcedido(permiso: Permiso): Boolean = permiso in concedidos
    override fun abrirAjustesDeLaApp() {
        vecesAbrioAjustes++
    }

    fun conceder(permiso: Permiso) {
        concedidos += permiso
    }

    fun revocar(permiso: Permiso) {
        concedidos -= permiso
    }
}

/**
 * Fake de [ProveedorUbicacion] -- "el proveedor de ubicación falso" que pide `PROMPT_FASE_08A.md §3.1`.
 * [EstrategiaFake.NuncaResponde] existe para el test de la trampa #3 (`A-04` nunca bloquea el guardado
 * esperando un fix que no llega): suspende para siempre, solo la sale un `withTimeoutOrNull` del lado del
 * `ViewModel` bajo prueba.
 */
class ProveedorUbicacionFake(private var estrategia: EstrategiaFake = EstrategiaFake.Inmediato(ResultadoUbicacion.NoDisponible)) :
    ProveedorUbicacion {

    sealed interface EstrategiaFake {
        data class Inmediato(val resultado: ResultadoUbicacion) : EstrategiaFake
        data object NuncaResponde : EstrategiaFake
    }

    fun responderCon(resultado: ResultadoUbicacion) {
        estrategia = EstrategiaFake.Inmediato(resultado)
    }

    fun noResponderNunca() {
        estrategia = EstrategiaFake.NuncaResponde
    }

    override suspend fun obtenerUbicacionActual(): ResultadoUbicacion = when (val actual = estrategia) {
        is EstrategiaFake.Inmediato -> actual.resultado
        EstrategiaFake.NuncaResponde -> awaitCancellation()
    }
}
