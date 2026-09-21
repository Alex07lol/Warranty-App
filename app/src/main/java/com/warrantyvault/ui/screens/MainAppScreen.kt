package com.warrantyvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.warrantyvault.ui.components.NavItem
import com.warrantyvault.ui.components.NavItems
import com.warrantyvault.ui.components.WarrantyBottomNav

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Products : Screen("products", "Products", Icons.Default.Description)
    object Scan : Screen("scan", "Scan OCR", Icons.Default.QrCodeScanner)
    object Notifications : Screen("notifications", "Alerts", Icons.Default.Notifications)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainAppScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        Screen.Dashboard,
        Screen.Products,
        Screen.Scan,
        Screen.Notifications,
        Screen.Settings
    )

    val selectedIndex = items.indexOfFirst { currentDestination?.route?.startsWith(it.route) == true }.takeIf { it >= 0 } ?: 0

    Scaffold(
        bottomBar = {
            WarrantyBottomNav(
                items = NavItems,
                selectedIndex = if (selectedIndex >= 0) selectedIndex else 0,
                onItemClick = { index ->
                    val screen = items[index]
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToProducts = { navController.navigate(Screen.Products.route) },
                    onNavigateToProductDetail = { id -> navController.navigate("product_detail/$id") }
                )
            }
            composable(Screen.Products.route) {
                ProductsScreen(
                    onProductClick = { id -> navController.navigate("product_detail/$id") },
                    onAddProductClick = { navController.navigate("add_product") }
                )
            }
            composable("product_detail/{productId}", arguments = listOf(navArgument("productId") { type = NavType.LongType })) { backStackEntry ->
                val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
                ProductDetailScreen(
                    productId = productId,
                    onBackClick = { navController.popBackStack() },
                    onEditClick = { id -> navController.navigate("edit_product/$id") }
                )
            }
            composable("add_product") {
                AddEditProductScreen(
                    productId = null,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("edit_product/{productId}", arguments = listOf(navArgument("productId") { type = NavType.LongType })) { backStackEntry ->
                val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
                AddEditProductScreen(
                    productId = productId,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.Scan.route) {
                ScanOcrScreen(
                    onDocumentSaved = { navController.navigate(Screen.Products.route) }
                )
            }
            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    onProductClick = { id -> navController.navigate("product_detail/$id") }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}