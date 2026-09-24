package com.warrantyvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.WarrantyEngine
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.backup.DriveBackupService
import com.warrantyvault.ui.components.*
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors

@Composable
fun DashboardScreen(
    unreadCount: Int,
    onNavigateToAlerts: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToRepairs: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProductDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val productDao = app.database.productDao()

    val products by productDao.getAllProducts(app.currentUserId).collectAsState(initial = emptyList())

    // Live Drive backup status: the background worker publishes into this flow, so the banner
    // reflects a sync that finished while the dashboard was already on screen.
    val driveState by DriveBackupService.observe(context).collectAsState()

    val statusOf = { p: com.warrantyvault.data.Product ->
        WarrantyEngine.warrantyStatusOf(p.purchaseDate, p.warrantyExpiryDate).status
    }
    val activeCount = products.count { statusOf(it) == "active" || statusOf(it) == "expiring_soon" || statusOf(it) == "not_started" }
    val expiringSoonList = products.filter { statusOf(it) == "expiring_soon" }
    val expiredCount = products.count { statusOf(it) == "expired" }

    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()

    WarrantyBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = WvDimens.ScreenGutter,
                end = WvDimens.ScreenGutter,
                top = 8.dp,
                bottom = WvDimens.Space6
            ),
            verticalArrangement = Arrangement.spacedBy(WvDimens.Space4)
        ) {
            // ---- Header ----
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "WarrantyVault",
                            style = MaterialTheme.typography.headlineMedium,
                            color = wv.textPrimary
                        )
                        Text(
                            "Your devices. Your peace of mind.",
                            style = MaterialTheme.typography.bodySmall,
                            color = wv.textSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WvDimens.Space1)) {
                        // Notification bell with unread badge
                        Box {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle),
                                modifier = Modifier
                                    .size(42.dp)
                                    .semantics { contentDescription = "Alerts" }
                            ) {
                                IconButton(onClick = onNavigateToAlerts) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = wv.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                        .background(wv.error, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (unreadCount > 9) "9+" else unreadCount.toString(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        // Avatar
                        Surface(
                            shape = CircleShape,
                            color = wv.primarySoft,
                            modifier = Modifier
                                .size(42.dp)
                                .semantics { contentDescription = "Profile" }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "A",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = wv.primary
                                )
                            }
                        }
                    }
                }
            }

            // ---- Drive sync status (absent until Drive backup is set up) ----
            if (driveState.linked) {
                item {
                    DriveSyncBanner(
                        state = driveState,
                        onClick = onNavigateToSettings
                    )
                }
            }

            // ---- Hero ----
            item {
                Surface(
                    shape = RoundedCornerShape(WvDimens.RadiusLarge),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle)
                ) {
                    Box(
                        modifier = Modifier
                            .background(wv.heroBrush)
                            .padding(WvDimens.Space5)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Keep track.\nStay worry-free.",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = wv.textPrimary
                                )
                                Spacer(Modifier.height(WvDimens.Space2))
                                Text(
                                    "Scan your receipts and warranty cards, and never miss an expiry again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = wv.textSecondary
                                )
                            }
                            Spacer(Modifier.width(WvDimens.Space3))
                            Surface(
                                shape = RoundedCornerShape(WvDimens.RadiusMedium),
                                color = wv.primarySoft,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = wv.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- Statistics ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(WvDimens.Space3)) {
                    ActiveWarrantiesStat(activeCount.toString(), Modifier.weight(1f))
                    ExpiringSoonStat(expiringSoonList.size.toString(), Modifier.weight(1f))
                    ExpiredWarrantiesStat(expiredCount.toString(), Modifier.weight(1f))
                }
            }

            // ---- Primary actions ----
            item {
                Column(verticalArrangement = Arrangement.spacedBy(WvDimens.Space3)) {
                    WarrantyPrimaryButton(
                        text = "Scan Document",
                        onClick = onNavigateToScan,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        icon = Icons.Default.Description
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(WvDimens.Space3)) {
                        WarrantyGhostButton(
                            text = "Add Manually",
                            onClick = onNavigateToProducts,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.AddCircleOutline
                        )
                        WarrantyGhostButton(
                            text = "Import Data",
                            onClick = onNavigateToRepairs,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.UploadFile
                        )
                    }
                }
            }

            // ---- Expiring soon ----
            if (expiringSoonList.isNotEmpty()) {
                item {
                    WarrantySectionHeader(
                        title = "Expiring Soon",
                        action = { SeeAllAction(onClick = onNavigateToProducts) }
                    )
                }
                items(expiringSoonList.size) { i ->
                    val p = expiringSoonList[i]
                    WarrantyProductCard(p, onClick = { onNavigateToProductDetail(p.id) })
                }
            }

            // ---- Recent products ----
            item {
                WarrantySectionHeader(
                    title = "Recent Products",
                    action = { SeeAllAction(onClick = onNavigateToProducts) }
                )
            }
            if (products.isEmpty()) {
                item { EmptyStateCard("No products yet", "Scan a receipt or add your first product manually.", "Add Product", onNavigateToProducts) }
            } else {
                items(products.take(5).size) { i ->
                    val p = products[i]
                    WarrantyProductCard(p, onClick = { onNavigateToProductDetail(p.id) })
                }
            }
        }
    }
}

/** Reusable intentional empty state. */
@Composable
fun EmptyStateCard(
    title: String,
    body: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Surface(
        shape = RoundedCornerShape(WvDimens.RadiusMedium),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(WvDimens.Space6).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WvDimens.Space2)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = wv.textPrimary)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = wv.textSecondary,
                modifier = Modifier.padding(horizontal = WvDimens.Space4)
            )
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(WvDimens.Space1))
                WarrantySmallButton(text = actionText, onClick = onAction)
            }
        }
    }
}
