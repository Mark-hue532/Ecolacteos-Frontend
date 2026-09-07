package com.ecolacteos.acopio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.ecolacteos.acopio.presentation.comun.DestinoSplash
import com.ecolacteos.acopio.ui.screens.acopio.BuscarProveedorScreen
import com.ecolacteos.acopio.ui.screens.acopio.ConfirmarComunicadoScreen
import com.ecolacteos.acopio.ui.screens.acopio.DetalleRegistroAcopioScreen
import com.ecolacteos.acopio.ui.screens.acopio.EscanearQrScreen
import com.ecolacteos.acopio.ui.screens.acopio.HistorialProveedorScreen
import com.ecolacteos.acopio.ui.screens.acopio.RegistrarAcopioScreen
import com.ecolacteos.acopio.ui.screens.acopio.RutaDelDiaScreen
import com.ecolacteos.acopio.ui.screens.calidad.DetalleAnalisisScreen
import com.ecolacteos.acopio.ui.screens.calidad.HomeCalidadScreen
import com.ecolacteos.acopio.ui.screens.calidad.RegistrarAnalisisScreen
import com.ecolacteos.acopio.ui.screens.calidad.SeleccionarRegistroAnalisisScreen
import com.ecolacteos.acopio.ui.screens.comun.AjustesScreen
import com.ecolacteos.acopio.ui.screens.comun.ComunicadosScreen
import com.ecolacteos.acopio.ui.screens.comun.EstadoSincronizacionScreen
import com.ecolacteos.acopio.ui.screens.comun.HomeScreen
import com.ecolacteos.acopio.ui.screens.comun.LoginScreen
import com.ecolacteos.acopio.ui.screens.comun.PendientesScreen
import com.ecolacteos.acopio.ui.screens.comun.SplashScreen
import com.ecolacteos.acopio.ui.screens.ventas.DetalleVentaScreen
import com.ecolacteos.acopio.ui.screens.ventas.HomeVentasScreen
import com.ecolacteos.acopio.ui.screens.ventas.RegistrarVentaScreen

/**
 * Grafo de navegación de esta fase (`MOBILE_SCREENS.md §2`, 7 destinos). Reglas de `§2.1` implementadas
 * acá, no en cada pantalla:
 *
 * 1. `S-03 Home` es la raíz del back stack -- Login limpia el stack entero al navegar a Home (regla 1).
 * 2. Los formularios (`V-02`) son destinos de pantalla completa, nunca diálogos (regla 2) -- ya lo son, al
 *    ser un `composable()` más.
 * 3. Guardar navega hacia atrás (`popBackStack()`), nunca hacia adelante (regla 3).
 * 6. Al morir el proceso, la Activity siempre remonta `AcopioNavHost` con `startDestination = SPLASH`, así
 *    que "reabre Home, no la última pantalla" sale gratis de esta estructura (regla 6) -- no hay
 *    restauración de `NavController` entre procesos en esta fase.
 */
@Composable
fun AcopioNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Rutas.SPLASH) {
        composable(Rutas.SPLASH) {
            SplashScreen(
                onNavegar = { destino ->
                    val destinoRuta = if (destino == DestinoSplash.HOME) Rutas.HOME else Rutas.LOGIN
                    navController.navigate(destinoRuta) {
                        popUpTo(Rutas.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Rutas.LOGIN) {
            LoginScreen(
                onLoginExitoso = {
                    navController.navigate(Rutas.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Rutas.HOME) {
            HomeScreen(
                onNavegarARegistrarVenta = { navController.navigate(Rutas.ventasRegistrar()) },
                onNavegarAHomeVentas = { navController.navigate(Rutas.VENTAS_HOME) },
                onNavegarAEstadoSincronizacion = { navController.navigate(Rutas.ESTADO_SINCRONIZACION) },
                onNavegarARutaAcopio = { navController.navigate(Rutas.ACOPIO_RUTA) },
                onNavegarAEscanearQrAcopio = { navController.navigate(Rutas.ACOPIO_ESCANEAR) },
                onNavegarAHomeCalidad = { navController.navigate(Rutas.CALIDAD_HOME) },
                onNavegarAPendientes = { navController.navigate(Rutas.PENDIENTES) },
                onNavegarAComunicados = { navController.navigate(Rutas.COMUNICADOS) },
                onNavegarAAjustes = { navController.navigate(Rutas.AJUSTES) },
            )
        }
        composable(Rutas.ESTADO_SINCRONIZACION) {
            EstadoSincronizacionScreen(onNavegarAPendientes = { navController.navigate(Rutas.PENDIENTES) })
        }
        composable(Rutas.VENTAS_HOME) {
            HomeVentasScreen(
                onNavegarARegistrar = { navController.navigate(Rutas.ventasRegistrar()) },
                onNavegarADetalle = { uuidCliente -> navController.navigate(Rutas.ventasDetalle(uuidCliente)) },
            )
        }
        composable(
            route = Rutas.VENTAS_REGISTRAR,
            arguments = listOf(
                navArgument(Rutas.ARG_UUID_CLIENTE_EDITAR) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val uuidClienteAEditar = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_UUID_CLIENTE_EDITAR) }
            RegistrarVentaScreen(onGuardadoConExito = { navController.popBackStack() }, uuidClienteAEditar = uuidClienteAEditar)
        }
        composable(
            route = Rutas.VENTAS_DETALLE,
            arguments = listOf(navArgument(Rutas.ARG_UUID_CLIENTE) { type = NavType.StringType }),
        ) { backStackEntry ->
            val uuidCliente = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_UUID_CLIENTE) }.orEmpty()
            DetalleVentaScreen(uuidCliente = uuidCliente)
        }

        // Fase 8B -- comunes (MOBILE_SCREENS.md §4): S-05, S-06, S-07.
        composable(Rutas.PENDIENTES) {
            PendientesScreen(
                onNavegarAEditarAcopio = { uuidCliente -> navController.navigate(Rutas.acopioEditar(uuidCliente)) },
                onNavegarAEditarVenta = { uuidCliente -> navController.navigate(Rutas.ventasEditar(uuidCliente)) },
            )
        }
        composable(Rutas.COMUNICADOS) {
            ComunicadosScreen(
                onNavegarAConfirmar = { comunicadoId -> navController.navigate(Rutas.acopioConfirmarComunicado(comunicadoId)) },
            )
        }
        composable(Rutas.AJUSTES) {
            AjustesScreen(
                onNavegarAPendientes = { navController.navigate(Rutas.PENDIENTES) },
                onSesionCerrada = {
                    navController.navigate(Rutas.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // Fase 8A -- ACOPIADOR (MOBILE_SCREENS.md §5). A-07 va en 8B.
        composable(Rutas.ACOPIO_RUTA) {
            RutaDelDiaScreen(
                onNavegarAEscanear = { navController.navigate(Rutas.ACOPIO_ESCANEAR) },
                onNavegarABuscar = { navController.navigate(Rutas.ACOPIO_BUSCAR) },
                onNavegarARegistrar = { proveedorId -> navController.navigate(Rutas.acopioRegistrar(proveedorId)) },
            )
        }
        composable(Rutas.ACOPIO_ESCANEAR) {
            EscanearQrScreen(
                onNavegarARegistrar = { proveedorId -> navController.navigate(Rutas.acopioRegistrar(proveedorId)) },
                onNavegarABuscar = { navController.navigate(Rutas.ACOPIO_BUSCAR) },
            )
        }
        composable(Rutas.ACOPIO_BUSCAR) {
            BuscarProveedorScreen(
                onNavegarARegistrar = { proveedorId -> navController.navigate(Rutas.acopioRegistrar(proveedorId)) },
            )
        }
        composable(
            route = Rutas.ACOPIO_REGISTRAR,
            arguments = listOf(
                navArgument(Rutas.ARG_PROVEEDOR_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Rutas.ARG_UUID_CLIENTE_EDITAR) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val proveedorId = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_PROVEEDOR_ID) }
            val uuidClienteAEditar = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_UUID_CLIENTE_EDITAR) }
            RegistrarAcopioScreen(
                proveedorId = proveedorId,
                uuidClienteAEditar = uuidClienteAEditar,
                onGuardadoConExito = { navController.popBackStack() },
                onNavegarAHistorial = { id -> navController.navigate(Rutas.acopioHistorial(id)) },
            )
        }
        composable(
            route = Rutas.ACOPIO_HISTORIAL,
            arguments = listOf(navArgument(Rutas.ARG_PROVEEDOR_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val proveedorId = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_PROVEEDOR_ID) }.orEmpty()
            HistorialProveedorScreen(
                proveedorId = proveedorId,
                onNavegarADetalle = { id -> navController.navigate(Rutas.acopioDetalle(id)) },
            )
        }
        composable(
            route = Rutas.ACOPIO_DETALLE,
            arguments = listOf(navArgument(Rutas.ARG_REGISTRO_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_REGISTRO_ID) }.orEmpty()
            DetalleRegistroAcopioScreen(id = id)
        }

        // Fase 8B -- A-07, la cuarta pantalla de ACOPIADOR (MOBILE_SCREENS.md §5).
        composable(
            route = Rutas.ACOPIO_CONFIRMAR_COMUNICADO,
            arguments = listOf(navArgument(Rutas.ARG_COMUNICADO_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val comunicadoId = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_COMUNICADO_ID) }.orEmpty()
            ConfirmarComunicadoScreen(comunicadoId = comunicadoId, onConfirmadoConExito = { navController.popBackStack() })
        }

        // Fase 8C -- CALIDAD (MOBILE_SCREENS.md §6): C-01..C-04. C-05..C-08 van en 8E.
        composable(Rutas.CALIDAD_HOME) {
            HomeCalidadScreen(
                onNavegarADetalleAnalisis = { id -> navController.navigate(Rutas.calidadDetalleAnalisis(id)) },
                onNavegarASeleccionarRegistro = { proveedorId -> navController.navigate(Rutas.calidadSeleccionarRegistro(proveedorId)) },
                onNavegarABuscarProveedor = { navController.navigate(Rutas.CALIDAD_BUSCAR_PROVEEDOR) },
            )
        }
        // Mismo `BuscarProveedorScreen`/`BuscarProveedorViewModel` de `A-03` (`§0` del prompt: la máquina de
        // búsqueda de proveedor ya existe, no se reimplementa) -- solo cambia adónde navega al elegir uno.
        composable(Rutas.CALIDAD_BUSCAR_PROVEEDOR) {
            BuscarProveedorScreen(
                onNavegarARegistrar = { proveedorId -> navController.navigate(Rutas.calidadSeleccionarRegistro(proveedorId)) },
            )
        }
        composable(
            route = Rutas.CALIDAD_SELECCIONAR_REGISTRO,
            arguments = listOf(navArgument(Rutas.ARG_PROVEEDOR_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val proveedorId = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_PROVEEDOR_ID) }.orEmpty()
            SeleccionarRegistroAnalisisScreen(
                proveedorId = proveedorId,
                onNavegarACapturar = { uuidCliente, serverId ->
                    navController.navigate(Rutas.calidadRegistrarAnalisis(uuidCliente, serverId))
                },
            )
        }
        composable(
            route = Rutas.CALIDAD_REGISTRAR_ANALISIS,
            arguments = listOf(
                navArgument(Rutas.ARG_REGISTRO_ACOPIO_UUID_CLIENTE) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Rutas.ARG_REGISTRO_ACOPIO_SERVER_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val uuidCliente = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_REGISTRO_ACOPIO_UUID_CLIENTE) }
            val serverId = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_REGISTRO_ACOPIO_SERVER_ID) }
            RegistrarAnalisisScreen(
                registroAcopioUuidCliente = uuidCliente,
                registroAcopioServerId = serverId,
                onGuardadoConExito = { navController.popBackStack() },
            )
        }
        composable(
            route = Rutas.CALIDAD_DETALLE_ANALISIS,
            arguments = listOf(navArgument(Rutas.ARG_REGISTRO_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_REGISTRO_ID) }.orEmpty()
            DetalleAnalisisScreen(registroAcopioId = id)
        }
    }
}
