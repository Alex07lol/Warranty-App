package com.warrantyvault.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors

@Composable
fun WarrantySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = wv.textPrimary
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = wv.textMuted
                )
            }
        }
        action?.invoke()
    }
}

/** Optional "See all" text action for section headers. */
@Composable
fun SeeAllAction(text: String = "See all", onClick: () -> Unit) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    TextButton(onClick = onClick) {
        Text(text, color = wv.primary, style = MaterialTheme.typography.labelLarge)
    }
}
