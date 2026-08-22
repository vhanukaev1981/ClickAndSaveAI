package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.V3Border
import com.example.ui.theme.V3MutedForeground
import com.example.ui.theme.V3Primary
import com.example.ui.theme.V3PrimarySoft
import com.example.ui.theme.V3Surface

private data class PremiumNavItem(
    val label: String,
    val selectedIcon: ImageVector?,
    val unselectedIcon: ImageVector?,
    val savingsGlyph: Boolean = false
)

@Composable
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val items = listOf(
        PremiumNavItem("בית", Icons.Filled.Home, Icons.Outlined.Home),
        PremiumNavItem("חיסכון", null, null, savingsGlyph = true),
        PremiumNavItem("AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
        PremiumNavItem(
            "לתשלום",
            Icons.AutoMirrored.Filled.ReceiptLong,
            Icons.AutoMirrored.Outlined.ReceiptLong
        ),
        PremiumNavItem("פרופיל", Icons.Filled.Person, Icons.Outlined.Person)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 4.dp)
            .testTag("bottom_nav_bar"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("premium_bottom_nav_dock"),
            shape = RoundedCornerShape(20.dp),
            color = V3Surface,
            border = BorderStroke(1.dp, V3Border),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    PremiumNavDestination(
                        item = item,
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        modifier = Modifier.weight(1f).then(premiumNavTestTag(index))
                    )
                }
            }
        }
    }
}

private fun premiumNavTestTag(index: Int): Modifier = when (index) {
    0 -> Modifier.testTag("nav_home")
    1 -> Modifier.testTag("nav_savings")
    2 -> Modifier.testTag("nav_ai")
    3 -> Modifier.testTag("nav_pay")
    4 -> Modifier.testTag("nav_profile")
    else -> Modifier
}

@Composable
private fun PremiumNavDestination(
    item: PremiumNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .background(if (selected) V3PrimarySoft else V3Surface)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(width = 30.dp, height = 23.dp),
            contentAlignment = Alignment.Center
        ) {
            if (item.savingsGlyph) {
                SavingsGlyph(
                    modifier = Modifier.size(21.dp),
                    tint = if (selected) V3Primary else V3MutedForeground,
                    contentDescription = item.label
                )
            } else {
                Icon(
                    imageVector = if (selected) item.selectedIcon!! else item.unselectedIcon!!,
                    contentDescription = item.label,
                    tint = if (selected) V3Primary else V3MutedForeground,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        Text(
            text = item.label,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) V3Primary else V3MutedForeground,
            maxLines = 1
        )
    }
}
