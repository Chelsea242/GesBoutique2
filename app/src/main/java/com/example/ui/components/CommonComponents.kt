package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.StatutDisponibilite
import com.example.data.StatutVente
import com.example.ui.theme.*

@Composable
fun StockStatusBadge(
    statut: StatutDisponibilite,
    modifier: Modifier = Modifier
) {
    val (bg, fg, border, icon) = when (statut) {
        StatutDisponibilite.EN_RUPTURE -> Quad(RuptureBackground, RuptureText, RuptureBorder, Icons.Filled.Error)
        StatutDisponibilite.STOCK_FAIBLE -> Quad(StockFaibleBackground, StockFaibleText, StockFaibleBorder, Icons.Filled.Warning)
        StatutDisponibilite.DISPONIBLE -> Quad(DisponibleBackground, DisponibleText, DisponibleBorder, Icons.Filled.CheckCircle)
        StatutDisponibilite.ARCHIVE -> Quad(ArchiveBackground, ArchiveText, AppOutline, Icons.Filled.Inventory2)
    }

    Surface(
        modifier = modifier,
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = statut.libelle,
                modifier = Modifier.size(13.dp),
                tint = fg
            )
            Text(
                text = statut.libelle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SaleStatusBadge(
    statutStr: String,
    modifier: Modifier = Modifier
) {
    val statut = StatutVente.fromString(statutStr)
    val isCancelled = statut == StatutVente.ANNULEE
    val bg = if (isCancelled) RuptureBackground else DisponibleBackground
    val fg = if (isCancelled) RuptureText else DisponibleText
    val border = if (isCancelled) RuptureBorder else DisponibleBorder

    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = statut.libelle,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * Carte bannière exécutive avec dégradé Slate-Cobalt et cercles géométriques subtils.
 */
@Composable
fun HeroGradientCard(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(SlateNavy, CobaltDeep, ActionBlue),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 3.dp,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(colors = colors))
                .drawBehind {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = size.height * 0.85f,
                        center = Offset(size.width * 0.92f, size.height * 0.15f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = size.height * 0.55f,
                        center = Offset(size.width * 0.78f, size.height * 0.95f)
                    )
                }
                .padding(20.dp),
            content = content
        )
    }
}

data class PillTabItem(
    val label: String,
    val icon: ImageVector,
    val badge: String? = null,
    val testTag: String = ""
)

@Composable
fun SegmentedPillTabs(
    items: List<PillTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppOutlineSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val bgColor by animateColorAsState(
                    targetValue = if (selected) AppSurface else Color.Transparent,
                    label = "pillTabBg"
                )
                val fgColor by animateColorAsState(
                    targetValue = if (selected) ActionBlue else TextSecondary,
                    label = "pillTabFg"
                )
                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(index) }
                        .minimumInteractiveComponentSize()
                        .let { if (item.testTag.isNotEmpty()) it.testTag(item.testTag) else it }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = fgColor
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) TextPrimary else TextSecondary,
                            maxLines = 1
                        )
                        if (!item.badge.isNullOrBlank()) {
                            Surface(
                                color = if (selected) ActionBlueSoft else AppOutlineSubtle,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = item.badge,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                    color = if (selected) ActionBlueDark else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProportionProgressBar(
    fraction: Float,
    color: Color = ActionBlue,
    trackColor: Color = AppSurfaceVariant,
    height: Dp = 7.dp,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "proportionBar"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
    ) {
        if (animatedFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun InitialsAvatar(
    name: String,
    containerColor: Color = ActionBlueSoft,
    contentColor: Color = ActionBlueDark,
    size: Dp = 42.dp
) {
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "U" }

    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppOutlineSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = ActionBlueSoft,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(28.dp),
                    tint = ActionBlue
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
