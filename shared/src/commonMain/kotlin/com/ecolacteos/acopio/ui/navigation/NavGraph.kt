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
import com.ecolacteos.acopio.ui.screens.acopio.DetalleRegistroAcopioScreen
import com.ecolacteos.acopio.ui.screens.acopio.EscanearQrScreen
import com.ecolacteos.acopio.ui.screens.acopio.HistorialProveedorScreen
import com.ecolacteos.acopio.ui.screens.acopio.RegistrarAcopioScreen
import com.ecolacteos.acopio.ui.screens.acopio.RutaDelDiaScreen
import com.ecolacteos.acopio.ui.screens.comun.EstadoSincronizacionScreen
import com.ecolacteos.acopio.ui.screens.comun.HomeScreen
import com.ecolacteos.acopio.ui.screens.comun.LoginScreen
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
                onNavegarARegistrarVenta = { navController.navigate(Rutas.VENTAS_REGISTRAR) },
                onNavegarAHomeVentas = { navController.navigate(Rutas.VENTAS_HOME) },
                onNavegarAEstadoSincronizacion = { navController.navigate(Rutas.ESTADO_SINCRONIZACION) },
                onNavegarARutaAcopio = { navController.navigate(Rutas.ACOPIO_RUTA) },
                onNavegarAEscanearQrAcopio = { navController.navigate(Rutas.ACOPIO_ESCANEAR) },
            )
        }
        composable(Rutas.ESTADO_SINCRONIZACION) { EstadoSincronizacionScreen() }
        composable(Rutas.VENTAS_HOME) {
            HomeVentasScreen(
                onNavegarARegistrar = { navController.navigate(Rutas.VENTAS_REGISTRAR) },
                onNavegarADetalle = { uuidCliente -> navController.navigate(Rutas.ventasDetalle(uuidCliente)) },
            )
        }
        composable(Rutas.VENTAS_REGISTRAR) {
            RegistrarVentaScreen(onGuardadoConExito = { navController.popBackStack() })
        }
        composable(
            route = Rutas.VENTAS_DETALLE,
            arguments = listOf(navArgument(Rutas.ARG_UUID_CLIENTE) { type = NavType.StringType }),
        ) { backStackEntry ->
            val uuidCliente = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_UUID_CLIENTE) }.orEmpty()
            DetalleVentaScreen(uuidCliente = uuidCliente)
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
            arguments = listOf(navArgument(Rutas.ARG_PROVEEDOR_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val proveedorId = backStackEntry.arguments?.read { getStringOrNull(Rutas.ARG_PROVEEDOR_ID) }.orEmpty()
            RegistrarAcopioScreen(
                proveedorId = proveedorId,
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
    }
}
