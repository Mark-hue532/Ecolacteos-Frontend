package com.ecolacteos.acopio.di

import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepository
import com.ecolacteos.acopio.data.repository.AnalisisCalidadRepositoryImpl
import com.ecolacteos.acopio.data.repository.BorradorFormularioRepository
import com.ecolacteos.acopio.data.repository.BorradorFormularioRepositoryImpl
import com.ecolacteos.acopio.data.repository.CatalogoRepository
import com.ecolacteos.acopio.data.repository.CatalogoRepositoryImpl
import com.ecolacteos.acopio.data.repository.ComunicadoConfirmacionRepository
import com.ecolacteos.acopio.data.repository.ComunicadoConfirmacionRepositoryImpl
import com.ecolacteos.acopio.data.repository.CorreccionRegistroRepository
import com.ecolacteos.acopio.data.repository.CorreccionRegistroRepositoryImpl
import com.ecolacteos.acopio.data.repository.InnovacionRepository
import com.ecolacteos.acopio.data.repository.InnovacionRepositoryImpl
import com.ecolacteos.acopio.data.repository.LoteProduccionRepository
import com.ecolacteos.acopio.data.repository.LoteProduccionRepositoryImpl
import com.ecolacteos.acopio.data.repository.PagoRepository
import com.ecolacteos.acopio.data.repository.PagoRepositoryImpl
import com.ecolacteos.acopio.data.repository.RecepcionPlantaRepository
import com.ecolacteos.acopio.data.repository.RecepcionPlantaRepositoryImpl
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepository
import com.ecolacteos.acopio.data.repository.RegistroAcopioRepositoryImpl
import com.ecolacteos.acopio.data.repository.ResolutorPadreRegistroAcopio
import com.ecolacteos.acopio.data.repository.VentaRepository
import com.ecolacteos.acopio.data.repository.VentaRepositoryImpl
import org.koin.dsl.module

/**
 * `data/repository/` (`PROMPT_FASE_06.md §2`): una interfaz pública + una implementación por agregado,
 * más [ResolutorPadreRegistroAcopio] compartido entre `AnalisisCalidadRepository`/`LoteProduccionRepository`
 * (`§4.2`) -- no se expone como binding propio porque es `internal`, cada uno lo construye directo.
 */
val repositoryModule = module {
    single { ResolutorPadreRegistroAcopio(registrosLocal = get(), cacheLocal = get(), apiClient = get()) }

    single<RegistroAcopioRepository> {
        RegistroAcopioRepositoryImpl(gestorSesion = get(), local = get(), cacheLocal = get(), apiClient = get(), syncEngine = get())
    }
    single<VentaRepository> { VentaRepositoryImpl(gestorSesion = get(), local = get(), syncEngine = get(), apiClient = get()) }
    single<AnalisisCalidadRepository> {
        AnalisisCalidadRepositoryImpl(gestorSesion = get(), local = get(), resolutor = get(), syncEngine = get(), apiClient = get())
    }
    single<LoteProduccionRepository> {
        LoteProduccionRepositoryImpl(gestorSesion = get(), local = get(), resolutor = get(), syncEngine = get(), apiClient = get())
    }
    single<CatalogoRepository> {
        CatalogoRepositoryImpl(catalogosLocal = get(), rutaZonaLocal = get(), apiClient = get(), syncEngine = get())
    }
    single<BorradorFormularioRepository> { BorradorFormularioRepositoryImpl(local = get()) }
    single<CorreccionRegistroRepository> { CorreccionRegistroRepositoryImpl(apiClient = get()) }
    single<ComunicadoConfirmacionRepository> { ComunicadoConfirmacionRepositoryImpl(apiClient = get()) }

    // Fase 8E (PROMPT_FASE_08E.md §4): los 3 Repository que faltaban desde la Fase 6 -- RECEPCION y las
    // lecturas de CALIDAD. Mismo patrón que CorreccionRegistroRepository: ONLINE-ONLY, sin tabla local.
    single<RecepcionPlantaRepository> { RecepcionPlantaRepositoryImpl(apiClient = get()) }
    single<PagoRepository> { PagoRepositoryImpl(apiClient = get()) }
    single<InnovacionRepository> { InnovacionRepositoryImpl(apiClient = get()) }
}
