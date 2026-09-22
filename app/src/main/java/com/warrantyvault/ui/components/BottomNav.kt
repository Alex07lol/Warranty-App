package com.warrantyvault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warrantyvault.ui.theme.WvDimens
import com.warrantyvault.ui.theme.darkWvColors
import com.warrantyvault.ui.theme.lightWvColors

data class NavItem(
    val title: String,
    val icon: ImageVector
)

/**
 * Floating glass bottom navigation. Translucent pill above content with border +
 * shadow, gesture-inset aware. Selected item gets a small tinted pill and stronger
 * icon/text — never a giant filled capsule. Optional badge (e.g. unread alerts).
 */
@Composable
fun WarrantyBottomNav(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badgeCounts: Map<Int, Int> = emptyMap()
) {
    val wv = if (isSystemInDarkTheme()) darkWvColors() else lightWvColors()
    val navShape = RoundedCornerShape(WvDimens.RadiusGlassNav)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = WvDimens.NavHorizontalPad, vertical = WvDimens.NavBottomPad)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(WvDimens.NavHeight)
                .shadow(elevation = 18.dp, shape = navShape, ambientColor = Color.Black, spotColor = Color.Black.copy(alpha = 0.35f))
                .background(color = wv.glassNav, shape = navShape)
                .border(width = 1.dp, color = wv.glassBorder, shape = navShape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val iconAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.72f,
                    animationSpec = tween(180),
                    label = "navAlpha"
                )
                val tint by animateColorAsState(
                    targetValue = if (isSelected) wv.primary else wv.textSecondary,
                    animationSpec = tween(180),
                    label = "navTint"
                )
                val pillColor by animateColorAsState(
                    targetValue = if (isSelected) wv.primarySoft else Color.Transparent,
                    animationSpec = tween(180),
                    label = "navPill"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(WvDimens.RadiusPill))
                        .background(pillColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onItemClick(index) }
                        )
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = tint,
                                modifier = Modifier
                                    .size(if (isSelected) 23.dp else 22.dp)
                                    .alpha(iconAlpha)
                            )
                            val badge = badgeCounts[index] ?: 0
                            if (badge > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 7.dp, y = (-3).dp)
                                        .size(16.dp)
                                        .background(wv.error, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (badge > 9) "9+" else badge.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.title,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = tint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
