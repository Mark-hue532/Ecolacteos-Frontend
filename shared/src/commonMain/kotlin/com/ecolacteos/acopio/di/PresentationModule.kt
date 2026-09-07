package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.presentation.acopio.BuscarProveedorViewModel
import com.ecolacteos.acopio.presentation.acopio.DetalleRegistroAcopioViewModel
import com.ecolacteos.acopio.presentation.acopio.EscanearQrViewModel
import com.ecolacteos.acopio.presentation.acopio.HistorialProveedorViewModel
import com.ecolacteos.acopio.presentation.acopio.RegistrarAcopioViewModel
import com.ecolacteos.acopio.presentation.acopio.RutaDelDiaViewModel
import com.ecolacteos.acopio.presentation.comun.EstadoSincronizacionViewModel
import com.ecolacteos.acopio.presentation.comun.HomeViewModel
import com.ecolacteos.acopio.presentation.comun.LoginViewModel
import com.ecolacteos.acopio.presentation.comun.SplashViewModel
import com.ecolacteos.acopio.presentation.ventas.DetalleVentaViewModel
import com.ecolacteos.acopio.presentation.ventas.HomeVentasViewModel
import com.ecolacteos.acopio.presentation.ventas.RegistrarVentaViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Los 7 `ViewModel` de la Fase 7 (`PROMPT_FASE_07.md §2.2`), registrados con el DSL `viewModel` de
 * `koin-compose-viewmodel` -- no `single`/`factory`: ese DSL integra con `ViewModelStoreOwner` de Compose
 * (`koinViewModel()` en cada `@Composable` de pantalla, `ui/navigation/`), así sobreviven a la recomposición
 * y a la rotación igual que un `ViewModel` de Android normal.
 */
val presentationModule = module {
    viewModel { SplashViewModel(get(), get()) }
    viewModel { LoginViewModel(get(), get()) }
    viewModel { HomeViewModel(gestorSesion = get(), observarResumenSyncUseCase = get(), observarConectividadUseCase = get(), sincronizarAhoraUseCase = get()) }
    viewModel {
        EstadoSincronizacionViewModel(
            observarPendientesUseCase = get(),
            observarResumenSyncUseCase = get(),
            observarConectividadUseCase = get(),
            observarEstadoSyncUseCase = get(),
            sincronizarAhoraUseCase = get(),
        )
    }
    viewModel { HomeVentasViewModel(get(), get()) }
    viewModel { RegistrarVentaViewModel(crearVentaUseCase = get(), observarCatalogosUseCase = get(), observarConectividadUseCase = get(), borradorFormularioUseCase = get()) }
    viewModel { params -> DetalleVentaViewModel(uuidCliente = params.get(), obtenerDetalleVentaUseCase = get(), observarCatalogosUseCase = get()) }

    // Fase 8A (PROMPT_FASE_08A.md §2): las 6 pantallas de ACOPIADOR.
    viewModel {
        RutaDelDiaViewModel(
            obtenerRutaDelDiaUseCase = get(),
            obtenerZonaAsignadaUseCase = get(),
            observarProveedoresVisitadosHoyUseCase = get(),
            observarConectividadUseCase = get(),
        )
    }
    viewModel { EscanearQrViewModel(resolverProveedorPorQrUseCase = get(), gestorPermisos = get()) }
    viewModel { BuscarProveedorViewModel(buscarProveedorPorNombreUseCase = get(), observarCatalogosUseCase = get()) }
    viewModel { params ->
        RegistrarAcopioViewModel(
            proveedorId = params.get(),
            crearRegistroAcopioUseCase = get(),
            observarCatalogosUseCase = get(),
            observarConectividadUseCase = get(),
            borradorFormularioUseCase = get(),
            gestorPermisos = get(),
            proveedorUbicacion = get(),
        )
    }
    viewModel { params ->
        HistorialProveedorViewModel(
            proveedorId = params.get(),
            observarHistorialProveedorUseCase = get(),
            obtenerRegistrosDeProveedorUseCase = get(),
            observarConectividadUseCase = get(),
        )
    }
    viewModel { params ->
        DetalleRegistroAcopioViewModel(
            id = params.get(),
            obtenerDetalleRegistroAcopioUseCase = get(),
            observarCatalogosUseCase = get(),
            gestorSesion = get(),
        )
    }
}
