package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.domain.Formatters
import com.example.ui.StockFilterMode
import com.example.ui.components.SectionHeader
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    currentUser: UtilisateurEntity,
    parametres: ParametresMagasin,
    indicateurs: IndicateursJour,
    produits: List<ProduitAvecStock>,
    onOpenNewSale: () -> Unit,
    onOpenTodaySales: () -> Unit,
    onOpenStocksWithFilter: (StockFilterMode) -> Unit,
    onOpenProducts: () -> Unit
) {
    val isAdmin = currentUser.role == RoleUtilisateur.ADMINISTRATEUR.name
    val alertProducts = produits
        .filter { it.actif && it.stockDisponible <= it.seuilAlerte }
        .sortedBy { it.stockDisponible }
        .take(6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "Tableau de bord",
            subtitle = "Date métier : ${Formatters.formatDateMetierReadable(indicateurs.dateMetier)} • ${if (isAdmin) "Vue globale du magasin" else "Vos ventes du jour"}",
            trailing = {
                Button(
                    onClick = onOpenNewSale,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_new_sale_button")
                ) {
                    Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nouvelle vente")
                }
            }
        )

        // Grille adaptative des 5 indicateurs du jour (cliquables pour ouvrir la liste correspondante)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 700.dp) 3 else if (maxWidth >= 420.dp) 2 else 1
            val cards = listOf(
                KpiSpec(
                    tag = "kpi_montant_jour",
                    title = if (isAdmin) "Ventes validées du jour" else "Mes ventes du jour",
                    value = Formatters.formatMontant(indicateurs.montantVentesValidees, parametres.devise),
                    caption = "Hors ventes annulées",
                    icon = Icons.Filled.Payments,
                    containerColor = ActionBlueSoft,
                    accentColor = ActionBlue,
                    borderColor = Color(0xFFBFDBFE),
                    onClick = onOpenTodaySales
                ),
                KpiSpec(
                    tag = "kpi_nb_ventes",
                    title = "Nombre de ventes",
                    value = Formatters.formatEntier(indicateurs.nombreVentesValidees),
                    caption = "Tickets validés aujourd'hui",
                    icon = Icons.Filled.ReceiptLong,
                    containerColor = DisponibleBackground,
                    accentColor = DisponibleText,
                    borderColor = DisponibleBorder,
                    onClick = onOpenTodaySales
                ),
                KpiSpec(
                    tag = "kpi_unites_vendues",
                    title = "Unités vendues",
                    value = Formatters.formatEntier(indicateurs.unitesVendues),
                    caption = "Articles sortis aujourd'hui",
                    icon = Icons.Filled.ShoppingBag,
                    containerColor = AppSurfaceVariant,
                    accentColor = TextPrimary,
                    borderColor = AppOutline,
                    onClick = onOpenTodaySales
                ),
                KpiSpec(
                    tag = "kpi_ruptures",
                    title = "Références en rupture",
                    value = Formatters.formatEntier(indicateurs.referencesEnRupture),
                    caption = "Stock à 0 — vente bloquée",
                    icon = Icons.Filled.Error,
                    containerColor = if (indicateurs.referencesEnRupture > 0) RuptureBackground else AppSurface,
                    accentColor = if (indicateurs.referencesEnRupture > 0) RuptureText else TextSecondary,
                    borderColor = if (indicateurs.referencesEnRupture > 0) RuptureBorder else AppOutlineSubtle,
                    onClick = { onOpenStocksWithFilter(StockFilterMode.EN_RUPTURE) }
                ),
                KpiSpec(
                    tag = "kpi_stock_faible",
                    title = "Stocks faibles",
                    value = Formatters.formatEntier(indicateurs.referencesEnStockFaible),
                    caption = "Sous le seuil d'alerte",
                    icon = Icons.Filled.Warning,
                    containerColor = if (indicateurs.referencesEnStockFaible > 0) StockFaibleBackground else AppSurface,
                    accentColor = if (indicateurs.referencesEnStockFaible > 0) StockFaibleText else TextSecondary,
                    borderColor = if (indicateurs.referencesEnStockFaible > 0) StockFaibleBorder else AppOutlineSubtle,
                    onClick = { onOpenStocksWithFilter(StockFilterMode.STOCK_FAIBLE) }
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                cards.chunked(columns).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { spec ->
                            KpiCard(spec = spec, modifier = Modifier.weight(1f))
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Alertes de stock prioritaires (Ruptures en rouge en premier, puis stocks faibles en orange)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AppSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AppOutlineSubtle)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Alertes de stock actives",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            "Produits actifs nécessitant un réapprovisionnement",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    TextButton(
                        onClick = { onOpenStocksWithFilter(StockFilterMode.EN_RUPTURE) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Voir les stocks")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                if (alertProducts.isEmpty()) {
                    Surface(
                        color = DisponibleBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DisponibleBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DisponibleText)
                            Text(
                                "Aucune alerte active : tous les produits actifs dépassent leur seuil.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DisponibleText
                            )
                        }
                    }
                } else {
                    alertProducts.forEach { item ->
                        val isRupture = item.stockDisponible <= 0
                        Surface(
                            onClick = {
                                onOpenStocksWithFilter(
                                    if (isRupture) StockFilterMode.EN_RUPTURE else StockFilterMode.STOCK_FAIBLE
                                )
                            },
                            color = if (isRupture) RuptureBackground else StockFaibleBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isRupture) RuptureBorder else StockFaibleBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.nom,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${item.reference} • Seuil : ${item.seuilAlerte}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = TextSecondary
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${item.stockDisponible}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isRupture) RuptureText else StockFaibleText
                                    )
                                    StockStatusBadge(item.statut)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenProducts,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Inventory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Catalogue produits")
                    }
                    OutlinedButton(
                        onClick = { onOpenStocksWithFilter(StockFilterMode.TOUS) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Warehouse, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("État des stocks")
                    }
                }
            }
        }
    }
}

private data class KpiSpec(
    val tag: String,
    val title: String,
    val value: String,
    val caption: String,
    val icon: ImageVector,
    val containerColor: Color,
    val accentColor: Color,
    val borderColor: Color,
    val onClick: () -> Unit
)

@Composable
private fun KpiCard(spec: KpiSpec, modifier: Modifier = Modifier) {
    Surface(
        onClick = spec.onClick,
        modifier = modifier.testTag(spec.tag),
        color = AppSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, spec.borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = spec.containerColor,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                        tint = spec.accentColor
                    )
                }
            }
            Text(
                text = spec.value,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                ),
                color = spec.accentColor
            )
            Text(
                text = spec.caption,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
