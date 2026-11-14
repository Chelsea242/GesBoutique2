package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.domain.Formatters
import com.example.ui.components.*
import com.example.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    parametres: ParametresMagasin,
    dateDebut: String,
    dateFin: String,
    rapport: RapportPeriodeComplet,
    produits: List<ProduitAvecStock>,
    onLoadReport: (String, String) -> Unit,
    onExportReportCsv: (Uri) -> Unit,
    onExportStockCsv: (Uri, List<ProduitAvecStock>) -> Unit
) {
    val today = remember(parametres.fuseauHoraire) {
        Formatters.todayDateMetier(parametres.fuseauHoraire)
    }
    val sevenDaysAgo = remember(today) {
        runCatching { LocalDate.parse(today).minusDays(6).toString() }.getOrDefault(today)
    }
    val monthStart = remember(today) {
        runCatching { LocalDate.parse(today).withDayOfMonth(1).toString() }.getOrDefault(today)
    }

    var showDatePickerStart by remember { mutableStateOf(false) }
    var showDatePickerEnd by remember { mutableStateOf(false) }
    var breakdownTab by remember { mutableIntStateOf(0) } // 0 = Par produit, 1 = Par jour

    val reportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) onExportReportCsv(uri) }

    val stockCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) onExportStockCsv(uri, produits) }

    LaunchedEffect(Unit) {
        onLoadReport(dateDebut, dateFin)
    }

    val periodLabel = remember(dateDebut, dateFin, today) {
        when {
            dateDebut == today && dateFin == today -> "Aujourd'hui • ${Formatters.formatDateMetierReadable(today)}"
            dateDebut == dateFin -> "Journée du ${Formatters.formatDateMetierReadable(dateDebut)}"
            else -> "Du ${Formatters.formatDateMetierReadable(dateDebut)} au ${Formatters.formatDateMetierReadable(dateFin)}"
        }
    }

    val maxProduitMontant = remember(rapport.lignesProduits) {
        (rapport.lignesProduits.maxOfOrNull { it.montantVendu } ?: 0L).coerceAtLeast(1L)
    }
    val maxJourMontant = remember(rapport.lignesJours) {
        (rapport.lignesJours.maxOfOrNull { it.montantVendu } ?: 0L).coerceAtLeast(1L)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // En-tête + boutons d'export CSV
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = "Rapports & Exports",
                    subtitle = "Ventes validées • Fuseau ${parametres.fuseauHoraire}"
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { stockCsvLauncher.launch("stock_$today.csv") },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_stock_csv_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Inventory, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stock CSV")
                    }
                    Button(
                        onClick = { reportCsvLauncher.launch("rapport_${dateDebut}_$dateFin.csv") },
                        enabled = rapport.lignesProduits.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_report_csv_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rapport CSV")
                    }
                }
            }
        }

        // Filtres de période & sélecteur de dates
        item {
            Surface(
                color = AppSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AppOutlineSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Période d'analyse",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = dateDebut == today && dateFin == today,
                            onClick = { onLoadReport(today, today) },
                            label = { Text("Aujourd'hui") },
                            leadingIcon = if (dateDebut == today && dateFin == today) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = dateDebut == sevenDaysAgo && dateFin == today,
                            onClick = { onLoadReport(sevenDaysAgo, today) },
                            label = { Text("7 jours") },
                            leadingIcon = if (dateDebut == sevenDaysAgo && dateFin == today) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = dateDebut == monthStart && dateFin == today,
                            onClick = { onLoadReport(monthStart, today) },
                            label = { Text("Ce mois") },
                            leadingIcon = if (dateDebut == monthStart && dateFin == today) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePickerStart = true },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Du ${Formatters.formatDateMetierReadable(dateDebut)}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { showDatePickerEnd = true },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Au ${Formatters.formatDateMetierReadable(dateFin)}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Carte exécutive principale (Chiffre d'affaires + KPIs clés)
        item {
            HeroGradientCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Insights,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = periodLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Chiffre d'affaires validé",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = Formatters.formatMontant(rapport.totalValide, parametres.devise),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 34.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroMetricPill(
                        label = "Tickets",
                        value = Formatters.formatEntier(rapport.nombreVentesValidees),
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricPill(
                        label = "Unités",
                        value = "${Formatters.formatEntier(rapport.totalUnites)} u",
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricPill(
                        label = "Panier moyen",
                        value = Formatters.formatMontant(rapport.panierMoyen, parametres.devise),
                        modifier = Modifier.weight(1.35f)
                    )
                }
            }
        }

        // Répartition par mode de paiement
        item {
            PaymentMixCard(
                repartition = rapport.repartitionPaiements,
                totalValide = rapport.totalValide,
                devise = parametres.devise
            )
        }

        // Bannière d'information si des ventes ont été annulées sur la période
        if (rapport.nombreAnnulations > 0) {
            item {
                Surface(
                    color = RuptureBackground.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, RuptureBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = RuptureText,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${rapport.nombreAnnulations} vente(s) annulée(s) (${Formatters.formatMontant(rapport.montantAnnulations, parametres.devise)}) — exclues des montants et quantités ci-dessus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RuptureText
                        )
                    }
                }
            }
        }

        // Onglets de ventilation détaillée : Par produit / Par jour
        item {
            SegmentedPillTabs(
                items = listOf(
                    PillTabItem(
                        label = "Par produit",
                        icon = Icons.Filled.Inventory2,
                        badge = "${rapport.lignesProduits.size}",
                        testTag = "report_tab_products"
                    ),
                    PillTabItem(
                        label = "Par jour",
                        icon = Icons.Filled.CalendarMonth,
                        badge = "${rapport.lignesJours.size}",
                        testTag = "report_tab_days"
                    )
                ),
                selectedIndex = breakdownTab,
                onSelect = { breakdownTab = it }
            )
        }

        // Contenu ventilé
        if (rapport.lignesProduits.isEmpty() && rapport.lignesJours.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.BarChart,
                    title = "Aucune vente validée sur cette période",
                    description = "Les ventes annulées sont automatiquement exclues des montants et quantités."
                )
            }
        } else if (breakdownTab == 0) {
            itemsIndexed(
                items = rapport.lignesProduits,
                key = { _, item -> "prod_${item.produitId}" }
            ) { index, item ->
                val shareOfTotal = if (rapport.totalValide > 0) {
                    ((item.montantVendu * 100.0) / rapport.totalValide).toInt()
                } else 0
                val barFraction = item.montantVendu.toFloat() / maxProduitMontant.toFloat()

                ProductReportRowCard(
                    rank = index + 1,
                    item = item,
                    sharePercent = shareOfTotal,
                    barFraction = barFraction,
                    devise = parametres.devise
                )
            }
        } else {
            items(
                items = rapport.lignesJours,
                key = { "day_${it.dateMetier}" }
            ) { day ->
                val barFraction = day.montantVendu.toFloat() / maxJourMontant.toFloat()
                DailyReportRowCard(
                    day = day,
                    barFraction = barFraction,
                    devise = parametres.devise
                )
            }
        }
    }

    if (showDatePickerStart) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerStart = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        val end = if (picked > dateFin) picked else dateFin
                        onLoadReport(picked, end)
                    }
                    showDatePickerStart = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePickerStart = false }) { Text("Annuler") } }
        ) { DatePicker(state = state) }
    }

    if (showDatePickerEnd) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        val start = if (picked < dateDebut) picked else dateDebut
                        onLoadReport(start, picked)
                    }
                    showDatePickerEnd = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePickerEnd = false }) { Text("Annuler") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun HeroMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PaymentMixCard(
    repartition: List<RapportPaiementLigne>,
    totalValide: Long,
    devise: String
) {
    Surface(
        color = AppSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppOutlineSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Répartition par paiement",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${repartition.sumOf { it.nombreVentes }} encaissement(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }

            // Barre segmentée multi-modes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AppSurfaceVariant)
            ) {
                if (totalValide > 0L) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        repartition.forEach { ligne ->
                            if (ligne.montant > 0L) {
                                val weight = (ligne.montant.toFloat() / totalValide.toFloat()).coerceAtLeast(0.02f)
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .background(colorForPaymentMode(ligne.mode))
                                )
                            }
                        }
                    }
                }
            }

            // Lignes détaillées par mode
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repartition.forEach { ligne ->
                    val pct = if (totalValide > 0L) ((ligne.montant * 100.0) / totalValide).toInt() else 0
                    val accent = colorForPaymentMode(ligne.mode)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Text(
                                text = ligne.mode.libelle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                text = "(${ligne.nombreVentes})",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "$pct %",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                color = TextSecondary
                            )
                            Text(
                                text = Formatters.formatMontant(ligne.montant, devise),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductReportRowCard(
    rank: Int,
    item: RapportProduitLigne,
    sharePercent: Int,
    barFraction: Float,
    devise: String
) {
    val rankBg = when (rank) {
        1 -> ActionBlue
        2 -> CobaltBright.copy(alpha = 0.18f)
        3 -> AmberAccent.copy(alpha = 0.16f)
        else -> AppSurfaceVariant
    }
    val rankFg = when (rank) {
        1 -> Color.White
        2 -> ActionBlueDark
        3 -> StockFaibleText
        else -> TextSecondary
    }

    Surface(
        color = AppSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppOutlineSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = rankBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "#$rank",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold
                            ),
                            color = rankFg
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.nom,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.reference} • ${item.unitesVendues} u vendue(s)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = TextMuted
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatMontant(item.montantVendu, devise),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "$sharePercent % du CA",
                        style = MaterialTheme.typography.labelSmall,
                        color = ActionBlue
                    )
                }
            }

            ProportionProgressBar(
                fraction = barFraction,
                color = if (rank == 1) ActionBlue else CobaltBright,
                height = 6.dp
            )
        }
    }
}

@Composable
private fun DailyReportRowCard(
    day: RapportJourLigne,
    barFraction: Float,
    devise: String
) {
    Surface(
        color = AppSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppOutlineSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = ActionBlueSoft,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.CalendarToday,
                                contentDescription = null,
                                tint = ActionBlueDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = Formatters.formatDateMetierReadable(day.dateMetier),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "${day.nombreVentes} vente(s) validée(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
                Text(
                    text = Formatters.formatMontant(day.montantVendu, devise),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ActionBlueDark
                )
            }
            ProportionProgressBar(
                fraction = barFraction,
                color = EmeraldAccent,
                height = 6.dp
            )
        }
    }
}

private fun colorForPaymentMode(mode: ModePaiement): Color = when (mode) {
    ModePaiement.ESPECES -> EmeraldAccent
    ModePaiement.MOBILE_MONEY -> ActionBlue
    ModePaiement.CARTE -> IndigoAccent
}
