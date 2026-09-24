package com.warrantyvault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import com.warrantyvault.ui.theme.WvTheme

data class NavItem(
    val title: String,
    val icon: ImageVector
)

/**
 * Floating glass bottom navigation, matching the web app's `.bottom-nav` pill: a **dark** glass
 * capsule in *both* themes (`--nav-bg`), a hairline white border (`--nav-line`), blur-scale shadow,
 * muted `--nav-ink` labels, and an active item filled by the brand gradient with white ink and a
 * blue glow. Stroke-level copy of the CSS, so light mode looks the same on the phone as in the
 * browser. Optional badge (e.g. unread alerts).
 */
@Composable
fun WarrantyBottomNav(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badgeCounts: Map<Int, Int> = emptyMap()
) {
    val wv = WvTheme.colors
    val pill = RoundedCornerShape(WvDimens.RadiusPill)
    val itemShape = RoundedCornerShape(WvDimens.RadiusLarge)

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
                .shadow(
                    elevation = 22.dp,
                    shape = pill,
                    ambientColor = Color.Black,
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .background(color = wv.glassNav, shape = pill)
                .border(width = 1.dp, color = wv.glassBorder, shape = pill)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex

                // The active pill fades in as the brand gradient (web `.nav-item.active`).
                val activeProgress by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(180),
                    label = "navActive"
                )
                val tint by animateColorAsState(
                    targetValue = if (isSelected) wv.onPrimary else wv.navInk,
                    animationSpec = tween(180),
                    label = "navTint"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(itemShape)
                        .background(brush = wv.brandBrush, shape = itemShape, alpha = activeProgress)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onItemClick(index) }
                        )
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = tint,
                                modifier = Modifier
                                    .size(20.dp)
                                    .alpha(if (isSelected) 1f else 0.9f)
                            )
                            val badge = badgeCounts[index] ?: 0
                            if (badge > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 9.dp, y = (-6).dp)
                                        .size(16.dp)
                                        .background(wv.error, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (badge > 9) "9+" else badge.toString(),
                                        color = wv.onAccent,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = item.title,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
