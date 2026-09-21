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
import com.warrantyvault.WarrantyVaultApplication
import com.warrantyvault.data.Notification
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
    onProductClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WarrantyVaultApplication
    val notificationDao = app.database.notificationDao()
    val notifications by notificationDao.getAllNotifications(app.currentUserId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()

    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    Column(
        Modifier
            .fillMaxSize()
            .background(wv.background)
            .padding(horizontal = WvDimens.ScreenGutter)
    ) {
        Spacer(Modifier.height(WvDimens.Space4))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Alerts", style = MaterialTheme.typography.headlineMedium, color = wv.textPrimary)
            if (notifications.any { !it.isRead }) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { notificationDao.markAllAsRead(app.currentUserId) }
                }) { Text("Mark all read", color = wv.primary) }
            }
        }
        Spacer(Modifier.height(WvDimens.Space4))

        if (notifications.isEmpty()) {
            EmptyStateCard(
                title = "No notifications yet",
                body = "Warranty expiry reminders and product updates will appear here."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(WvDimens.Space3),
                contentPadding = PaddingValues(bottom = WvDimens.Space6)
            ) {
                items(notifications.size) { i ->
                    val n = notifications[i]
                    NotificationItem(
                        notification = n,
                        dateFormat = dateFormat,
                        onClick = {
                            n.productId?.let(onProductClick)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Neutral surface card. Unread: small dot + stronger title + slightly elevated surface.
 * Type accent (amber expiring / red expired / blue info / green success) is a small dot
 * only — never a border or giant coloured card.
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
        shape = RoundedCornerShape(WvDimens.RadiusMedium),
        color = if (isUnread) wv.surfaceElevated else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isUnread) wv.border else wv.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(WvDimens.Space3), verticalAlignment = Alignment.Top) {
            // Accent dot (semantic, small)
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(WvDimens.Space3))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Medium,
                        color = wv.textPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = dateFormat.format(Date(notification.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = wv.textMuted
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = wv.textSecondary
                )
            }
        }
    }
}
