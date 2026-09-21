package com.warrantyvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.ui.theme.*

@Composable
fun WarrantyBottomNav(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == CanvasDark

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    color = if (isDark) NavBgDark else NavBgLight,
                    shape = RoundedCornerShape(RadiusPill)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            color = if (isSelected) BrandPrimary else Color.Transparent,
                            shape = RoundedCornerShape(RadiusPill)
                        )
                        .clickable { onItemClick(index) }
                        .padding(horizontal = 4.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else NavInkLight,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.title,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else NavInkLight
                        )
                    }
                }
            }
        }
    }
}

data class NavItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val NavItems = listOf(
    NavItem("Dashboard", Icons.Default.Dashboard),
    NavItem("Products", Icons.Default.Description),
    NavItem("Scan", Icons.Default.QrCodeScanner),
    NavItem("Alerts", Icons.Default.Notifications),
    NavItem("Settings", Icons.Default.Settings)
)