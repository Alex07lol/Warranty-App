package com.warrantyvault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warrantyvault.backup.SyncState
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.WvTheme
import kotlinx.coroutines.delay

/**
 * Status banner for the Google Drive backup, shown on the dashboard once an account is linked.
 *
 * Renders nothing when Drive backup is not set up, so users who never enable it see no change.
 * Tapping it opens Settings, where the backup controls live.
 */
@Composable
fun DriveSyncBanner(
    state: SyncState,
    onClick: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Nothing to render and nothing to schedule until Drive backup is set up: most users never
    // enable it, and they should not pay for a ticking timer.
    if (!state.linked) return

    // "12 min ago" has to keep ticking while the banner is visible, or it silently becomes wrong.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val presentation = DriveSyncStatus.present(state, now)
    if (presentation.variant == DriveSyncStatus.Variant.HIDDEN) return

    val wv = WvTheme.colors
    val (accent, icon) = when (presentation.variant) {
        DriveSyncStatus.Variant.SYNCED -> wv.success to Icons.Default.CloudDone
        DriveSyncStatus.Variant.PENDING -> wv.warning to Icons.Default.CloudSync
        else -> wv.error to Icons.Default.CloudOff
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${presentation.title}. ${presentation.detail}" },
        shape = RoundedCornerShape(WvDimens.RadiusMedium),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, wv.borderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = WvDimens.Space4, vertical = WvDimens.Space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WvDimens.Space3)
        ) {
            Surface(
                shape = RoundedCornerShape(WvDimens.RadiusSmall),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    presentation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = wv.textPrimary
                )
                Text(
                    presentation.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = wv.textSecondary
                )
            }
            when (presentation.action) {
                // A tap already started an upload: show progress instead of letting it be tapped twice.
                DriveSyncStatus.Action.SYNCING -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = accent
                )

                DriveSyncStatus.Action.SYNC -> TextButton(onClick = onSyncNow) {
                    Text(
                        "Sync",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                }

                // No action here: the banner still opens Settings, so point that out.
                DriveSyncStatus.Action.NONE -> Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = wv.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
