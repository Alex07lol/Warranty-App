package com.warrantyvault.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.warrantyvault.ui.theme.*

@Composable
fun WarrantySearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search by name, brand, serial, or tag…"
) {
    val wv = WvTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = wv.textMuted,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = wv.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = placeholder,
                color = wv.textMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = wv.textPrimary),
        shape = RoundedCornerShape(WvDimens.RadiusSmall),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = wv.primary,
            unfocusedBorderColor = wv.border,
            focusedTextColor = wv.textPrimary,
            unfocusedTextColor = wv.textPrimary,
            cursorColor = wv.primary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}