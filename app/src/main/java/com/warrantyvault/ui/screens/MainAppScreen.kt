package com.warrantyvault.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.ui.components.NavItem
import com.warrantyvault.ui.components.WarrantyBottomNav

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object Products : Screen("products", "Products", Icons.Default.Devices)
    object Scan : Screen("scan", "Scan", Icons.Default.DocumentScanner)
    object Repairs : Screen("repairs", "Repairs", Icons.Default.Build)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    /** Reachable via the header notification bell — deliberately not a bottom tab. */
    object Notifications : Screen("notifications", "Alerts", Icons.Default.Build)
}

private val bottomDestinations = listOf(
    Screen.Dashboard,
    Screen.Products,
    Screen.Scan,
    Screen.Repairs,
    Screen.Settings
)

@Composable
fun MainAppScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val selectedIndex = bottomDestinations
        .indexOfFirst { currentRoute == it.route }
        .takeIf { it >= 0 } ?: 0

    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication

    // Unread badge for the bell shown on tab screens' headers.
    val unreadCount by remember {
        app.database.notificationDao().getUnreadCountFlow(app.currentUserId)
    }.collectAsState(initial = 0)

    val snackbarHostState = remember { SnackbarHostState() }

    val items = listOf(
        NavItem(Screen.Dashboard.title, Screen.Dashboard.icon),
        NavItem(Screen.Products.title, Screen.Products.icon),
        NavItem(Screen.Scan.title, Screen.Scan.icon),
        NavItem(Screen.Repairs.title, Screen.Repairs.icon),
        NavItem(Screen.Settings.title, Screen.Settings.icon)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in bottomDestinations.map { it.route }) {
                WarrantyBottomNav(
                    items = items,
                    selectedIndex = selectedIndex,
                    onItemClick = { index ->
                        val screen = bottomDestinations[index]
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    unreadCount = unreadCount,
                    onNavigateToAlerts = { navController.navigate(Screen.Notifications.route) },
                    onNavigateToProducts = { navController.navigate(Screen.Products.route) },
                    onNavigateToScan = { navController.navigate(Screen.Scan.route) },
                    onNavigateToRepairs = { navController.navigate(Screen.Repairs.route) },
                    onNavigateToProductDetail = { id -> navController.navigate("product_detail/$id") }
                )
            }
            composable(Screen.Products.route) {
                ProductsScreen(
                    onProductClick = { id -> navController.navigate("product_detail/$id") },
                    onAddProductClick = { navController.navigate("add_product") }
                )
            }
            composable(
                "product_detail/{productId}",
                arguments = listOf(navArgument("productId") { type = NavType.LongType })
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
                ProductDetailScreen(
                    productId = productId,
                    onBackClick = { navController.popBackStack() },
                    onEditClick = { id -> navController.navigate("edit_product/$id") }
                )
            }
            composable("add_product") {
                AddEditProductScreen(productId = null, onBackClick = { navController.popBackStack() })
            }
            composable(
                "edit_product/{productId}",
                arguments = listOf(navArgument("productId") { type = NavType.LongType })
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
                AddEditProductScreen(productId = productId, onBackClick = { navController.popBackStack() })
            }
            composable(Screen.Scan.route) {
                ScanOcrScreen(onDocumentSaved = { navController.navigate(Screen.Products.route) })
            }
            composable(Screen.Repairs.route) {
                RepairLocationsScreen()
            }
            composable(Screen.Notifications.route) {
                NotificationsScreen(onProductClick = { id -> navController.navigate("product_detail/$id") })
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
