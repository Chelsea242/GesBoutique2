package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.ParametresMagasin
import com.example.data.ProduitAvecStock
import com.example.data.RapportProduitLigne
import com.example.domain.Formatters
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    parametres: ParametresMagasin,
    dateDebut: String,
    dateFin: String,
    lignes: List<RapportProduitLigne>,
    annulationsResume: Pair<Int, Long>,
    produits: List<ProduitAvecStock>,
    onLoadReport: (String, String) -> Unit,
    onExportReportCsv: (Uri) -> Unit,
    onExportStockCsv: (Uri, List<ProduitAvecStock>) -> Unit
) {
    val today = remember(parametres.fuseauHoraire) {
        Formatters.todayDateMetier(parametres.fuseauHoraire)
    }
    var showDatePickerStart by remember { mutableStateOf(false) }
    var showDatePickerEnd by remember { mutableStateOf(false) }

    val reportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) onExportReportCsv(uri) }

    val stockCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) onExportStockCsv(uri, produits) }

    val totalMontant = remember(lignes) { lignes.sumOf { it.montantVendu } }
    val totalUnites = remember(lignes) { lignes.sumOf { it.unitesVendues } }

    LaunchedEffect(Unit) {
        onLoadReport(dateDebut, dateFin)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Rapports & Exports",
            subtitle = "Ventes validées (00h00 à 00h00 fuseau ${parametres.fuseauHoraire})",
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { stockCsvLauncher.launch("stock_$today.csv") },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("export_stock_csv_button")
                    ) {
                        Icon(Icons.Filled.Inventory, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stock CSV")
                    }
                    Button(
                        onClick = { reportCsvLauncher.launch("rapport_${dateDebut}_$dateFin.csv") },
                        enabled = lignes.isNotEmpty(),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("export_report_csv_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rapport CSV")
                    }
                }
            }
        )

        // Raccourcis de période + sélecteurs de dates
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = dateDebut == today && dateFin == today,
                onClick = { onLoadReport(today, today) },
                label = { Text("Aujourd'hui") }
            )
            val sevenDaysAgo = runCatching { LocalDate.parse(today).minusDays(6).toString() }.getOrDefault(today)
            FilterChip(
                selected = dateDebut == sevenDaysAgo && dateFin == today,
                onClick = { onLoadReport(sevenDaysAgo, today) },
                label = { Text("7 jours") }
            )
            val monthStart = runCatching { LocalDate.parse(today).withDayOfMonth(1).toString() }.getOrDefault(today)
            FilterChip(
                selected = dateDebut == monthStart && dateFin == today,
                onClick = { onLoadReport(monthStart, today) },
                label = { Text("Ce mois") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showDatePickerStart = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Du ${Formatters.formatDateMetierReadable(dateDebut)}")
            }
            OutlinedButton(
                onClick = { showDatePickerEnd = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Au ${Formatters.formatDateMetierReadable(dateFin)}")
            }
        }

        // Synthèse période
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                color = ActionBlueSoft,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Chiffre d'affaires validé", style = MaterialTheme.typography.labelMedium, color = ActionBlueDark)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        Formatters.formatMontant(totalMontant, parametres.devise),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                        color = ActionBlueDark
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                color = DisponibleBackground,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Unités vendues", style = MaterialTheme.typography.labelMedium, color = DisponibleText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${Formatters.formatEntier(totalUnites)} u",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                        color = DisponibleText
                    )
                }
            }
        }

        if (annulationsResume.first > 0) {
            Surface(
                color = RuptureBackground.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RuptureBorder)
            ) {
                Text(
                    text = "${annulationsResume.first} vente(s) annulée(s) sur cette période (${Formatters.formatMontant(annulationsResume.second, parametres.devise)}) — exclues des totaux ci-dessus.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = RuptureText
                )
            }
        }

        if (lignes.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Filled.BarChart,
                title = "Aucune vente validée sur cette période",
                description = "Les ventes annulées sont automatiquement exclues des montants et quantités."
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = AppSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AppOutlineSubtle)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // En-tête de tableau distinct
                    Surface(color = AppSurfaceVariant) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Produit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.4f), color = TextSecondary)
                            Text("Qté", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = TextSecondary)
                            Text("Montant", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = TextSecondary)
                        }
                    }
                    HorizontalDivider(color = AppOutlineSubtle)
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(lignes, key = { it.produitId }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.4f)) {
                                    Text(item.nom, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text(item.reference, style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono), color = TextMuted)
                                }
                                Text(
                                    text = "${item.unitesVendues}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.5f),
                                    color = TextPrimary
                                )
                                Text(
                                    text = Formatters.formatMontant(item.montantVendu, parametres.devise),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f),
                                    color = TextPrimary
                                )
                            }
                            HorizontalDivider(color = AppOutlineSubtle)
                        }
                    }
                }
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
