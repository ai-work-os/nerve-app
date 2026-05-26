package com.nerve.android.ui.app

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nerve.android.ui.theme.AmberPrimary
import com.nerve.android.ui.theme.StoneMain

@Composable
fun FloatingPillNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(bottom = 32.dp)
            .height(80.dp)
            .width(360.dp),
        shape = RoundedCornerShape(40.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillNavItem(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                icon = Icons.Default.SmartToy,
                label = "Hub"
            )
            PillNavItem(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                icon = Icons.Default.Forum,
                label = "Comms"
            )
            PillNavItem(
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                icon = Icons.Default.Mic,
                label = "Sense"
            )
            PillNavItem(
                selected = selectedTab == 3,
                onClick = { onTabSelected(3) },
                icon = Icons.Default.LightMode,
                label = "Brief"
            )
        }
    }
}

@Composable
private fun PillNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    AnimatedContent(
        targetState = selected,
        label = "pill_nav_anim",
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        }
    ) { isSelected ->
        if (isSelected) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(StoneMain)
                    .clickable { onClick() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(icon, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = AmberPrimary,
                    letterSpacing = 1.sp
                )
            }
        } else {
            IconButton(onClick = onClick) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
            }
        }
    }
}
