package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.domain.Formatters
import com.example.ui.StockFilterMode
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.*

@Composable
fun StocksScreen(
    isAdmin: Boolean,
    produits: List<ProduitAvecStock>,
    mouvements: List<MouvementAvecDetails>,
    parametres: ParametresMagasin,
    stockFilter: StockFilterMode,
    onSetStockFilter: (StockFilterMode) -> Unit,
    onRestock: (Long, Int, String, (ServiceResult<*>) -> Unit) -> Unit,
    onCorrectInventory: (Long, Int, String, (ServiceResult<*>) -> Unit) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) } // 0 = Disponibilités, 1 = Journal des mouvements
    var searchQuery by remember { mutableStateOf("") }
    var restockTarget by remember { mutableStateOf<ProduitAvecStock?>(null) }
    var correctionTarget by remember { mutableStateOf<ProduitAvecStock?>(null) }

    val filteredProduits = remember(produits, stockFilter, searchQuery) {
        produits.filter { p ->
            p.actif &&
                when (stockFilter) {
                    StockFilterMode.TOUS -> true
                    StockFilterMode.EN_RUPTURE -> p.stockDisponible <= 0
                    StockFilterMode.STOCK_FAIBLE -> p.stockDisponible in 1..p.seuilAlerte
                    StockFilterMode.DISPONIBLE -> p.stockDisponible > p.seuilAlerte
                } &&
                (searchQuery.isBlank() ||
                    p.nom.contains(searchQuery, ignoreCase = true) ||
                    p.reference.contains(searchQuery, ignoreCase = true))
        }.sortedBy { it.stockDisponible }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Suivi des stocks",
            subtitle = "Calculé par agrégation des mouvements signés"
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = tabIndex == 0,
                onClick = { tabIndex = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                modifier = Modifier.testTag("stocks_tab_disponibilites")
            ) {
                Text("Disponibilités")
            }
            SegmentedButton(
                selected = tabIndex == 1,
                onClick = { tabIndex = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                modifier = Modifier.testTag("stocks_tab_mouvements")
            ) {
                Text("Mouvements (${mouvements.size})")
            }
        }

        if (tabIndex == 0) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filtrer un produit par référence ou nom...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("stocks_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StockFilterMode.entries) { mode ->
                    FilterChip(
                        selected = stockFilter == mode,
                        onClick = { onSetStockFilter(mode) },
                        label = { Text(mode.libelle) },
                        modifier = Modifier.testTag("stock_filter_${mode.name}")
                    )
                }
            }

            if (filteredProduits.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.Inventory,
                    title = "Aucun article dans ce filtre",
                    description = "Sélectionnez un autre statut de disponibilité ou effacez votre recherche."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredProduits, key = { it.id }) { p ->
                        val isRupture = p.stockDisponible <= 0
                        val isLow = p.stockDisponible in 1..p.seuilAlerte
                        val bg = when {
                            isRupture -> RuptureBackground
                            isLow -> StockFaibleBackground
                            else -> AppSurface
                        }
                        val border = when {
                            isRupture -> RuptureBorder
                            isLow -> StockFaibleBorder
                            else -> AppOutlineSubtle
                        }

                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, border)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(p.nom, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        Text(
                                            "${p.reference} • Seuil : ${p.seuilAlerte}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                            color = TextSecondary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "${p.stockDisponible} u",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontFamily = JetBrainsMono,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isRupture) RuptureText else if (isLow) StockFaibleText else DisponibleText
                                        )
                                        StockStatusBadge(p.statut)
                                    }
                                }

                                if (isAdmin) {
                                    HorizontalDivider(color = AppOutlineSubtle)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { correctionTarget = p },
                                            modifier = Modifier.testTag("correct_stock_${p.reference}")
                                        ) {
                                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Inventaire")
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { restockTarget = p },
                                            modifier = Modifier.testTag("restock_${p.reference}")
                                        ) {
                                            Icon(Icons.Filled.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Entrée stock")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (mouvements.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.SwapVert,
                    title = "Aucun mouvement de stock",
                    description = "Les entrées initiales, ventes, réapprovisionnements et corrections apparaîtront ici."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mouvements, key = { it.id }) { m ->
                        val isPositive = m.quantiteSignee >= 0
                        val typeEnum = TypeMouvement.fromString(m.type)
                        Surface(
                            color = AppSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AppOutlineSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "${m.produitNom} (${m.produitReference})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "${typeEnum.libelle} • ${m.auteurNom} • ${Formatters.formatDateTime(m.dateHeureUtc, parametres.fuseauHoraire)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                    Text(
                                        m.motif,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (isPositive) DisponibleBackground else RuptureBackground,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isPositive) "+${m.quantiteSignee}" else "${m.quantiteSignee}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isPositive) DisponibleText else RuptureText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (restockTarget != null) {
        val target = restockTarget!!
        var qtyText by remember { mutableStateOf("") }
        var motifText by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { restockTarget = null },
            title = { Text("Réapprovisionner ${target.nom}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Stock actuel : ${target.stockDisponible}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() }; err = null },
                        label = { Text("Quantité reçue (> 0) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("restock_qty_input")
                    )
                    OutlinedTextField(
                        value = motifText,
                        onValueChange = { motifText = it; err = null },
                        label = { Text("Motif ou référence bon de livraison *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("restock_motif_input")
                    )
                    if (!err.isNullOrBlank()) {
                        Text(err!!, color = RuptureText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = qtyText.toIntOrNull() ?: 0
                        onRestock(target.id, qty, motifText) { res ->
                            if (res is ServiceResult.Succes) restockTarget = null
                            else if (res is ServiceResult.Erreur) err = res.message
                        }
                    },
                    modifier = Modifier.testTag("confirm_restock_button")
                ) {
                    Text("Valider l'entrée")
                }
            },
            dismissButton = {
                TextButton(onClick = { restockTarget = null }) { Text("Annuler") }
            }
        )
    }

    if (correctionTarget != null) {
        val target = correctionTarget!!
        var countedText by remember { mutableStateOf(target.stockDisponible.toString()) }
        var motifText by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        val counted = countedText.toIntOrNull()
        val ecart = if (counted != null) counted - target.stockDisponible else null

        AlertDialog(
            onDismissRequest = { correctionTarget = null },
            title = { Text("Correction d'inventaire") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${target.nom} (${target.reference}) — Stock théorique : ${target.stockDisponible}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = countedText,
                        onValueChange = { countedText = it.filter { c -> c.isDigit() }; err = null },
                        label = { Text("Quantité comptée (>= 0) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("inventory_counted_input")
                    )
                    if (ecart != null) {
                        Text(
                            text = "Écart calculé : ${if (ecart > 0) "+$ecart" else "$ecart"}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                            color = if (ecart == 0) TextMuted else if (ecart > 0) DisponibleText else StockFaibleText
                        )
                    }
                    OutlinedTextField(
                        value = motifText,
                        onValueChange = { motifText = it; err = null },
                        label = { Text("Justification obligatoire *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("inventory_motif_input")
                    )
                    if (!err.isNullOrBlank()) {
                        Text(err!!, color = RuptureText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = countedText.toIntOrNull() ?: -1
                        onCorrectInventory(target.id, qty, motifText) { res ->
                            if (res is ServiceResult.Succes) correctionTarget = null
                            else if (res is ServiceResult.Erreur) err = res.message
                        }
                    },
                    modifier = Modifier.testTag("confirm_inventory_button")
                ) {
                    Text("Appliquer")
                }
            },
            dismissButton = {
                TextButton(onClick = { correctionTarget = null }) { Text("Annuler") }
            }
        )
    }
}
