package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = statut.libelle,
                modifier = Modifier.size(14.dp),
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
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = statut.libelle,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
        shape = RoundedCornerShape(16.dp),
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
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
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
