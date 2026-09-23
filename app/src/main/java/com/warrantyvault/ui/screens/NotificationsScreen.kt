package com.warrantyvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.data.Notification
import com.warrantyvault.ui.components.WarrantyBackground
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(
    onProductClick: (Long) -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val notificationDao = app.database.notificationDao()
    val notifications by notificationDao.getAllNotifications(app.currentUserId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()

    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    WarrantyBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = WvDimens.ScreenGutter)
        ) {
            Spacer(Modifier.height(WvDimens.Space4))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBackClick != null) {
                        Surface(
                            shape = CircleShape,
                            color = wv.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, wv.borderSubtle),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onBackClick() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("‹", color = wv.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Light)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    Text("Alerts", style = MaterialTheme.typography.headlineMedium, color = wv.textPrimary)
                }
                if (notifications.any { !it.isRead }) {
                    TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) { notificationDao.markAllAsRead(app.currentUserId) }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Mark all read",
                            color = wv.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (notifications.isEmpty()) {
                EmptyStateCard(
                    title = "No notifications yet",
                    body = "Warranty expiry reminders and product updates will appear here."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 100.dp)
                ) {
                    items(notifications.size) { i ->
                        val n = notifications[i]
                        NotificationItem(
                            notification = n,
                            dateFormat = dateFormat,
                            onClick = {
                                if (!n.isRead) {
                                    scope.launch(Dispatchers.IO) {
                                        notificationDao.markAsRead(n.id)
                                    }
                                }
                                n.productId?.let(onProductClick)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Alert card matching app-preview.html .alert-card:
 * Unread: surfaceElevated background + border + full accent dot.
 * Read: surface background + subtle border + dimmed dot (opacity .35).
 */
@Composable
fun NotificationItem(
    notification: Notification,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    val isUnread = !notification.isRead

    val accent = when {
        notification.notificationType.contains("expired") -> wv.error
        notification.notificationType.contains("30_days") -> wv.warning
        notification.notificationType.contains("7_days") -> wv.warning
        notification.notificationType.contains("1_day") -> wv.warning
        notification.notificationType.contains("update") || notification.notificationType.contains("success") -> wv.success
        else -> wv.info
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isUnread) wv.surfaceElevated else wv.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isUnread) wv.border else wv.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Accent dot (8dp semantic dot)
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(accent.copy(alpha = if (isUnread) 1f else 0.35f), CircleShape)
            )
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontSize = 13.5.sp,
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Medium,
                        color = wv.textPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = dateFormat.format(Date(notification.createdAt)),
                        fontSize = 10.5.sp,
                        color = wv.textMuted
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = notification.message,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = wv.textSecondary
                )
            }
        }
    }
}
