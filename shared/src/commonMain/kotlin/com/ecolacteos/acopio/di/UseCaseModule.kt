package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.domain.VerificadorPendientes
import com.ecolacteos.acopio.domain.usecase.ActualizarRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.ActualizarVentaUseCase
import com.ecolacteos.acopio.domain.usecase.AnexarCorreccionUseCase
import com.ecolacteos.acopio.domain.usecase.BorradorFormularioUseCase
import com.ecolacteos.acopio.domain.usecase.BuscarAnalisisPorFolioUseCase
import com.ecolacteos.acopio.domain.usecase.BuscarProveedorPorNombreUseCase
import com.ecolacteos.acopio.domain.usecase.BuscarRecepcionesUseCase
import com.ecolacteos.acopio.domain.usecase.ClasificarPadresRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.ConfirmarComunicadoUseCase
import com.ecolacteos.acopio.domain.usecase.CrearAnalisisCalidadUseCase
import com.ecolacteos.acopio.domain.usecase.DescartarPendienteUseCase
import com.ecolacteos.acopio.domain.usecase.CrearLoteProduccionUseCase
import com.ecolacteos.acopio.domain.usecase.CrearRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.CrearVentaUseCase
import com.ecolacteos.acopio.domain.usecase.DecidirDestinoInicialUseCase
import com.ecolacteos.acopio.domain.usecase.LoginUseCase
import com.ecolacteos.acopio.domain.usecase.LogoutUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarCatalogosUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarConectividadUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarEntregasConEstadoAnalisisUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerAlertasPorZonaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleRecepcionUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerPagosDeProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerScoreConfianzaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerZonasDisponiblesUseCase
import com.ecolacteos.acopio.domain.usecase.RegistrarRecepcionUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarEstadoSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarHistorialProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarPendientesUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarProveedoresVisitadosHoyUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarResumenSyncUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarVentasDelDiaUseCase
import com.ecolacteos.acopio.domain.usecase.ObservarLotesRecientesUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleAnalisisCalidadUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleLoteUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleRegistroAcopioUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerDetalleVentaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRegistrosDeProveedorUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerRutaDelDiaUseCase
import com.ecolacteos.acopio.domain.usecase.ObtenerZonaAsignadaUseCase
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

    // Fase 8A (PROMPT_FASE_08A.md §0): los 4 UseCase que faltaban para ACOPIADOR.
    single { BuscarProveedorPorNombreUseCase(get()) }
    single { ObtenerDetalleRegistroAcopioUseCase(get()) }
    single { ObtenerZonaAsignadaUseCase(catalogoRepository = get(), gestorSesion = get()) }
    single { ObservarProveedoresVisitadosHoyUseCase(get()) }

    // Fase 8B (PROMPT_FASE_08B.md §5): S-05, S-07 y el modo edición de A-04/V-02.
    single { DescartarPendienteUseCase(registroAcopioRepository = get(), analisisCalidadRepository = get(), loteProduccionRepository = get(), ventaRepository = get()) }
    single { ActualizarRegistroAcopioUseCase(get()) }
    single { ActualizarVentaUseCase(get()) }

    // Fase 8C (PROMPT_FASE_08C.md §6): los 3 UseCase de CALIDAD (C-01..C-04).
    single { ClasificarPadresRegistroAcopioUseCase(get()) }
    single { ObservarEntregasConEstadoAnalisisUseCase(registroAcopioRepository = get(), analisisCalidadRepository = get()) }
    single { ObtenerDetalleAnalisisCalidadUseCase(get()) }

    // Fase 8D (PROMPT_FASE_08D.md §7): los 2 UseCase de PRODUCCION que necesitan DI (P-01, P-04).
    // La retención agregada de P-02 (`evaluarRetencionAgregada`) es una función pura, sin estado ni
    // dependencias -- se llama directo, no se registra acá.
    single { ObservarLotesRecientesUseCase(loteProduccionRepository = get(), observarCatalogosUseCase = get()) }
    single { ObtenerDetalleLoteUseCase(get()) }

    // Fase 8E (PROMPT_FASE_08E.md §7): RECEPCION y las lecturas de CALIDAD -- los 8 UseCase que faltaban.
    single { RegistrarRecepcionUseCase(get()) }
    single { BuscarRecepcionesUseCase(get()) }
    single { ObtenerDetalleRecepcionUseCase(get()) }
    single { ObtenerPagosDeProveedorUseCase(get()) }
    single { ObtenerAlertasPorZonaUseCase(get()) }
    single { ObtenerScoreConfianzaUseCase(get()) }
    single { BuscarAnalisisPorFolioUseCase(get()) }
    single { ObtenerZonasDisponiblesUseCase(get()) }

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
