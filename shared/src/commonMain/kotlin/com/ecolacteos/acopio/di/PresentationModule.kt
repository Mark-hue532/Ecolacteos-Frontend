package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.presentation.acopio.BuscarProveedorViewModel
import com.ecolacteos.acopio.presentation.acopio.ConfirmarComunicadoViewModel
import com.ecolacteos.acopio.presentation.acopio.DetalleRegistroAcopioViewModel
import com.ecolacteos.acopio.presentation.acopio.EscanearQrViewModel
import com.ecolacteos.acopio.presentation.acopio.HistorialProveedorViewModel
import com.ecolacteos.acopio.presentation.acopio.RegistrarAcopioViewModel
import com.ecolacteos.acopio.presentation.acopio.RutaDelDiaViewModel
import com.ecolacteos.acopio.presentation.comun.AjustesViewModel
import com.ecolacteos.acopio.presentation.comun.ComunicadosViewModel
import com.ecolacteos.acopio.presentation.comun.ConfirmacionesRecientesEnMemoria
import com.ecolacteos.acopio.presentation.comun.EstadoSincronizacionViewModel
import com.ecolacteos.acopio.presentation.comun.HomeViewModel
import com.ecolacteos.acopio.presentation.comun.LoginViewModel
import com.ecolacteos.acopio.presentation.comun.PendientesViewModel
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
    viewModel { params ->
        RegistrarVentaViewModel(
            crearVentaUseCase = get(),
            actualizarVentaUseCase = get(),
            ventaRepository = get(),
            observarCatalogosUseCase = get(),
            observarConectividadUseCase = get(),
            borradorFormularioUseCase = get(),
            uuidClienteAEditar = params.getOrNull(),
        )
    }
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
        // Dos parámetros posicionales -- ParametersHolder no tiene getOrNull(index), así que se leen por
        // índice explícito (ParametersHolder.get(Int), sin chequeo de nulidad en runtime por erasure).
        RegistrarAcopioViewModel(
            proveedorId = params.get<String>(0),
            crearRegistroAcopioUseCase = get(),
            actualizarRegistroAcopioUseCase = get(),
            registroAcopioRepository = get(),
            observarCatalogosUseCase = get(),
            observarConectividadUseCase = get(),
            borradorFormularioUseCase = get(),
            gestorPermisos = get(),
            proveedorUbicacion = get(),
            uuidClienteAEditar = params.get<String?>(1),
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

    // Fase 8B (PROMPT_FASE_08B.md §2): S-05, S-06, S-07 y A-07.
    single { ConfirmacionesRecientesEnMemoria() }
    viewModel {
        PendientesViewModel(
            observarPendientesUseCase = get(),
            observarCatalogosUseCase = get(),
            observarConectividadUseCase = get(),
            observarEstadoSyncUseCase = get(),
            reintentarManualUseCase = get(),
            descartarPendienteUseCase = get(),
            sincronizarAhoraUseCase = get(),
        )
    }
    viewModel { ComunicadosViewModel(observarCatalogosUseCase = get(), observarConectividadUseCase = get(), confirmacionesRecientesEnMemoria = get()) }
    viewModel {
        AjustesViewModel(
            gestorSesion = get(),
            logoutUseCase = get(),
            observarPendientesUseCase = get(),
            observarConectividadUseCase = get(),
            observarResumenSyncUseCase = get(),
            observarEstadoSyncUseCase = get(),
            sincronizarAhoraUseCase = get(),
        )
    }
    viewModel { params ->
        ConfirmarComunicadoViewModel(
            comunicadoId = params.get(),
            confirmarComunicadoUseCase = get(),
            buscarProveedorPorNombreUseCase = get(),
            observarCatalogosUseCase = get(),
            observarConectividadUseCase = get(),
            confirmacionesRecientesEnMemoria = get(),
        )
    }
}
