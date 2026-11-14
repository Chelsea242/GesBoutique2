package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.domain.Formatters
import com.example.ui.HistoryPeriodFilter
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.SaleStatusBadge
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun HistoryScreen(
    currentUser: UtilisateurEntity,
    ventes: List<VenteAvecVendeur>,
    utilisateurs: List<UtilisateurEntity>,
    parametres: ParametresMagasin,
    periodFilter: HistoryPeriodFilter,
    onSetPeriodFilter: (HistoryPeriodFilter) -> Unit,
    onLoadSaleDetail: suspend (Long) -> VenteDetailComplet?,
    onCancelSale: (Long, String, (VenteDetailComplet) -> Unit) -> Unit,
    onExportSalesCsv: (Uri, List<VenteAvecVendeur>) -> Unit,
    onFeedback: (String, Boolean) -> Unit
) {
    val isAdmin = currentUser.role == RoleUtilisateur.ADMINISTRATEUR.name
    val scope = rememberCoroutineScope()

    var searchNumero by remember { mutableStateOf("") }
    var selectedStatut by remember { mutableStateOf<StatutVente?>(null) }
    var selectedVendeurId by remember { mutableStateOf<Long?>(null) }

    var activeDetail by remember { mutableStateOf<VenteDetailComplet?>(null) }
    var showReceiptPreview by remember { mutableStateOf(false) }
    var cancelTargetId by remember { mutableStateOf<Long?>(null) }

    val today = remember(parametres.fuseauHoraire) {
        Formatters.todayDateMetier(parametres.fuseauHoraire)
    }
    val sevenDaysAgo = remember(today) {
        runCatching { LocalDate.parse(today).minusDays(6).toString() }.getOrDefault(today)
    }

    val filteredVentes = remember(ventes, searchNumero, selectedStatut, selectedVendeurId, periodFilter, today, sevenDaysAgo) {
        ventes.filter { v ->
            val matchNum = searchNumero.isBlank() ||
                v.numero.contains(searchNumero, ignoreCase = true) ||
                v.vendeurNom.contains(searchNumero, ignoreCase = true)
            val matchStatut = selectedStatut == null || v.statut == selectedStatut!!.name
            val matchVendeur = !isAdmin || selectedVendeurId == null || v.vendeurId == selectedVendeurId
            val matchPeriod = when (periodFilter) {
                HistoryPeriodFilter.AUJOURDHUI -> v.dateMetier == today
                HistoryPeriodFilter.SEPT_JOURS -> v.dateMetier >= sevenDaysAgo
                HistoryPeriodFilter.TOUTES -> true
            }
            matchNum && matchStatut && matchVendeur && matchPeriod
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportSalesCsv(uri, filteredVentes)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Historique des ventes",
            subtitle = "${filteredVentes.size} vente(s) • ${if (isAdmin) "Tout le magasin" else "Vos ventes"}",
            trailing = {
                if (isAdmin) {
                    OutlinedButton(
                        onClick = { csvLauncher.launch("ventes_$today.csv") },
                        enabled = filteredVentes.isNotEmpty(),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("export_sales_csv_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CSV")
                    }
                }
            }
        )

        OutlinedTextField(
            value = searchNumero,
            onValueChange = { searchNumero = it },
            placeholder = { Text("Rechercher par numéro de ticket...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("history_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryPeriodFilter.entries) { period ->
                FilterChip(
                    selected = periodFilter == period,
                    onClick = { onSetPeriodFilter(period) },
                    label = { Text(period.libelle) }
                )
            }
            items(StatutVente.entries) { statut ->
                FilterChip(
                    selected = selectedStatut == statut,
                    onClick = { selectedStatut = if (selectedStatut == statut) null else statut },
                    label = { Text(statut.libelle) }
                )
            }
            if (isAdmin && utilisateurs.size > 1) {
                items(utilisateurs, key = { it.id }) { u ->
                    FilterChip(
                        selected = selectedVendeurId == u.id,
                        onClick = { selectedVendeurId = if (selectedVendeurId == u.id) null else u.id },
                        label = { Text(u.nom) }
                    )
                }
            }
        }

        if (filteredVentes.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Filled.ReceiptLong,
                title = "Aucune vente enregistrée",
                description = "Aucune transaction ne correspond aux filtres sélectionnés."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredVentes, key = { it.id }) { v ->
                    val isCancelled = v.statut == StatutVente.ANNULEE.name
                    Surface(
                        onClick = {
                            scope.launch {
                                activeDetail = onLoadSaleDetail(v.id)
                            }
                        },
                        color = if (isCancelled) RuptureBackground.copy(alpha = 0.45f) else AppSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isCancelled) RuptureBorder else AppOutlineSubtle),
                        modifier = Modifier.testTag("sale_row_${v.numero}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = v.numero,
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${Formatters.formatDateTime(v.dateHeureUtc, parametres.fuseauHoraire)} • ${v.vendeurNom}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = ModePaiement.fromString(v.modePaiement).libelle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = Formatters.formatMontant(v.total, parametres.devise),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isCancelled) RuptureText else TextPrimary
                                )
                                SaleStatusBadge(v.statut)
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeDetail != null && !showReceiptPreview) {
        val detail = activeDetail!!
        val isCancelled = detail.vente.statut == StatutVente.ANNULEE.name
        AlertDialog(
            onDismissRequest = { activeDetail = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(detail.vente.numero, style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono))
                    SaleStatusBadge(detail.vente.statut)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Date : ${Formatters.formatDateTime(detail.vente.dateHeureUtc, parametres.fuseauHoraire)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        "Vendeur : ${detail.vente.vendeurNom} • ${ModePaiement.fromString(detail.vente.modePaiement).libelle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    HorizontalDivider(color = AppOutlineSubtle)
                    detail.lignes.forEach { l ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${l.nomSnapshot} (${l.quantite}×)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                Formatters.formatMontant(l.sousTotal, parametres.devise),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                    HorizontalDivider(color = AppOutlineSubtle)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.titleSmall)
                        Text(
                            Formatters.formatMontant(detail.vente.total, parametres.devise),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                            color = ActionBlue
                        )
                    }
                    if (detail.vente.modePaiement == ModePaiement.ESPECES.name) {
                        Text(
                            "Reçu : ${Formatters.formatMontant(detail.vente.montantRecu, parametres.devise)} • Monnaie : ${Formatters.formatMontant(detail.vente.monnaieRendue, parametres.devise)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    if (detail.annulation != null) {
                        Surface(
                            color = RuptureBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, RuptureBorder)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text("Annulée par ${detail.annulation.auteurNom}", style = MaterialTheme.typography.labelMedium, color = RuptureText)
                                Text("Motif : ${detail.annulation.motif}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isAdmin && !isCancelled) {
                        OutlinedButton(
                            onClick = { cancelTargetId = detail.vente.id },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RuptureText),
                            modifier = Modifier.testTag("open_cancel_sale_button")
                        ) {
                            Text("Annuler vente")
                        }
                    }
                    Button(
                        onClick = { showReceiptPreview = true },
                        modifier = Modifier.testTag("reprint_ticket_button")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ticket")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDetail = null }) { Text("Fermer") }
            }
        )
    }

    if (activeDetail != null && showReceiptPreview) {
        ReceiptDialog(
            detail = activeDetail!!,
            parametres = parametres,
            onDismiss = { showReceiptPreview = false },
            onPrintFeedback = onFeedback
        )
    }

    if (cancelTargetId != null) {
        var motif by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { cancelTargetId = null },
            title = { Text("Annuler la vente intégralement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "La vente sera conservée en historique comme annulée et les stocks seront rétablis en une transaction. Cette opération est irréversible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = motif,
                        onValueChange = { motif = it; err = null },
                        label = { Text("Motif d'annulation obligatoire *") },
                        singleLine = false,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("cancel_sale_motif_input")
                    )
                    if (!err.isNullOrBlank()) {
                        Text(err!!, color = RuptureText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (motif.trim().isEmpty()) {
                            err = "Veuillez saisir un motif d'annulation."
                        } else {
                            onCancelSale(cancelTargetId!!, motif) { updated ->
                                activeDetail = updated
                                cancelTargetId = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RuptureText),
                    modifier = Modifier.testTag("confirm_cancel_sale_button")
                ) {
                    Text("Confirmer l'annulation")
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelTargetId = null }) { Text("Retour") }
            }
        )
    }
}
