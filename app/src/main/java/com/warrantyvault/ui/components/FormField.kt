package com.warrantyvault.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WarrantyFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    error: Boolean = false,
    errorMessage: String? = null,
    minLines: Int = 1,
    maxLines: Int = 1,
    placeholder: String = ""
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            placeholder = { if (placeholder.isNotEmpty()) Text(text = placeholder, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = maxLines == 1,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            shape = RoundedCornerShape(RadiusMD),
            trailingIcon = trailingIcon,
            isError = error,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        if (error && errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun WarrantyFormFieldDate(
    value: Long?,
    onValueChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    errorMessage: String? = null
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateString = value?.let { dateFormat.format(Date(it)) } ?: ""

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = dateString,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            readOnly = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            shape = RoundedCornerShape(RadiusMD),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Select date",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            isError = error,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        if (error && errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}