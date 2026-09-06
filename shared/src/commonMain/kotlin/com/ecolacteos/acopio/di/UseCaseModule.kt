package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.domain.VerificadorPendientes
import com.ecolacteos.acopio.domain.usecase.AnexarCorreccionUseCase
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.ConfirmarComunicadoUseCase
import com.ecolacteos.acopio.domain.usecase.CrearAnalisisCalidadUseCase
import com.ecolacteos.acopio.domain.usecase.CrearLoteProduccionUseCase
import com.ecolacteos.acopio.domain.usecase.CrearRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearVentaUseCase
import com.ecolacteos.acopio.domain.usecase.DecidirDestinoInicialUseCase
import com.ecolacteos.acopio.domain.usecase.LoginUseCase
import com.ecolacteos.acopio.domain.usecase.LogoutUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarEstadoSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarHistorialProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarPendientesUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarResumenSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarVentasDelDiaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleVentaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRegistrosDeProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRutaDelDiaUseCase
import com.ecolacteos.acopio.domain.usecase.RefrescarSesionUseCase
import com.ecolacteos.acopio.domain.usecase.ReintentarManualUseCase
import com.ecolacteos.acopio.domain.usecase.ResolverProveedorPorQrUseCase
import com.ecolacteos.acopio.domain.usecase.SincronizarAhoraUseCase
import com.ecolacteos.acopio.domain.usecase.VerificarPendientesUseCase
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * `domain/usecase/` (`PROMPT_FASE_06.md §5-6`). [VerificarPendientesUseCase] se registra con `bind` a
 * [VerificadorPendientes] además de su propio tipo -- reemplaza el binding que `di/SecurityModule.kt`
 * (Fase 3) hacía sobre el stub eliminado; `GestorSesionImpl` sigue pidiendo `get<VerificadorPendientes>()`
 * sin cambios, Koin resuelve el mismo singleton por cualquiera de los dos tipos.
 */
val useCaseModule = module {
    single { CrearRegistroAcopioUseCase(get()) }
    single { CrearVentaUseCase(get()) }
    single { CrearAnalisisCalidadUseCase(get()) }
    single { CrearLoteProduccionUseCase(get()) }
    single { ObtenerRegistrosDeProveedorUseCase(get()) }
    single { ObservarHistorialProveedorUseCase(get()) }
    single { ObservarPendientesUseCase(registroAcopioRepository = get(), analisisCalidadRepository = get(), loteProduccionRepository = get(), ventaRepository = get()) }
    single { ReintentarManualUseCase(registroAcopioRepository = get(), analisisCalidadRepository = get(), loteProduccionRepository = get(), ventaRepository = get()) }
    single { ObservarCatalogosUseCase(get()) }
    single { ObtenerRutaDelDiaUseCase(get()) }
    single { ResolverProveedorPorQrUseCase(get()) }
    single { ConfirmarComunicadoUseCase(get()) }
    single { AnexarCorreccionUseCase(get()) }

    // Fase 7 (PROMPT_FASE_07.md §2.4): las pantallas comunes y la vertical de Venta.
    single { LoginUseCase(get()) }
    single { DecidirDestinoInicialUseCase(get()) }
    single { RefrescarSesionUseCase(get()) }
    single { SincronizarAhoraUseCase(get()) }
    single { ObservarConectividadUseCase(get()) }
    single { ObservarEstadoSyncUseCase(get()) }
    single { ObservarResumenSyncUseCase(observarPendientesUseCase = get(), catalogoRepository = get()) }
    single { ObservarVentasDelDiaUseCase(get()) }
    single { ObtenerDetalleVentaUseCase(get()) }
    single { BorradorFormularioUseCase(get()) }

    single {
        VerificarPendientesUseCase(registroAcopioRepository = get(), analisisCalidadRepository = get(), loteProduccionRepository = get(), ventaRepository = get())
    } bind VerificadorPendientes::class

    single {
        LogoutUseCase(
            gestorSesion = get(),
            verificarPendientes = get(),
            registroAcopioRepository = get(),
            analisisCalidadRepository = get(),
            loteProduccionRepository = get(),
            ventaRepository = get(),
            catalogoRepository = get(),
        )
    }
}
