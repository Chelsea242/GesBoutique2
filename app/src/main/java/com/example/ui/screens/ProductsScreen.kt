package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.ProductActiveFilter
import com.example.ui.ProductSortOption
import com.example.ui.StockFilterMode
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.*

@Composable
fun ProductsScreen(
    isAdmin: Boolean,
    produits: List<ProduitAvecStock>,
    categories: List<CategorieEntity>,
    parametres: ParametresMagasin,
    onCreateCategory: (String, (Boolean) -> Unit) -> Unit,
    onSaveProduct: (
        editingId: Long?,
        reference: String,
        nom: String,
        categorieId: Long?,
        prixVente: Long,
        seuilAlerte: Int,
        stockInitial: Int,
        onResult: (ServiceResult<*>) -> Unit
    ) -> Unit,
    onToggleActive: (Long, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var activeFilter by remember { mutableStateOf(ProductActiveFilter.ACTIFS) }
    var stockFilter by remember { mutableStateOf(StockFilterMode.TOUS) }
    var sortOption by remember { mutableStateOf(ProductSortOption.NOM_ASC) }

    var showProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProduitAvecStock?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    val filteredProducts = remember(produits, searchQuery, selectedCategoryId, activeFilter, stockFilter, sortOption) {
        val list = produits.filter { p ->
            val matchActive = when (activeFilter) {
                ProductActiveFilter.ACTIFS -> p.actif
                ProductActiveFilter.ARCHIVES -> !p.actif
                ProductActiveFilter.TOUS -> true
            }
            val matchCat = selectedCategoryId == null || p.categorieId == selectedCategoryId
            val matchStock = when (stockFilter) {
                StockFilterMode.TOUS -> true
                StockFilterMode.EN_RUPTURE -> p.actif && p.stockDisponible <= 0
                StockFilterMode.STOCK_FAIBLE -> p.actif && p.stockDisponible in 1..p.seuilAlerte
                StockFilterMode.DISPONIBLE -> p.actif && p.stockDisponible > p.seuilAlerte
            }
            val matchSearch = searchQuery.isBlank() ||
                p.nom.contains(searchQuery, ignoreCase = true) ||
                p.reference.contains(searchQuery, ignoreCase = true)
            matchActive && matchCat && matchStock && matchSearch
        }
        when (sortOption) {
            ProductSortOption.NOM_ASC -> list.sortedBy { it.nom.lowercase() }
            ProductSortOption.REF_ASC -> list.sortedBy { it.reference }
            ProductSortOption.PRIX_DESC -> list.sortedByDescending { it.prixVente }
            ProductSortOption.STOCK_ASC -> list.sortedBy { it.stockDisponible }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Catalogue produits",
            subtitle = "${filteredProducts.size} produit(s) affiché(s)",
            trailing = {
                if (isAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showCategoryDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("add_category_button")
                        ) {
                            Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Catégorie")
                        }
                        Button(
                            onClick = {
                                editingProduct = null
                                showProductDialog = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("new_product_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nouveau")
                        }
                    }
                }
            }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Rechercher par référence ou nom...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Effacer")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("products_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ProductActiveFilter.entries) { filter ->
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { activeFilter = filter },
                    label = { Text(filter.libelle) }
                )
            }
            items(StockFilterMode.entries.filter { it != StockFilterMode.TOUS }) { mode ->
                FilterChip(
                    selected = stockFilter == mode,
                    onClick = { stockFilter = if (stockFilter == mode) StockFilterMode.TOUS else mode },
                    label = { Text(mode.libelle) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (categories.isNotEmpty()) {
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        AssistChip(
                            onClick = { selectedCategoryId = null },
                            label = { Text(if (selectedCategoryId == null) "Catégorie : Toutes" else "Toutes") }
                        )
                    }
                    items(categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = if (selectedCategoryId == cat.id) null else cat.id },
                            label = { Text(cat.nom) }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            var sortMenuOpen by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(sortOption.libelle)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    ProductSortOption.entries.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.libelle) },
                            onClick = {
                                sortOption = opt
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        if (filteredProducts.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Filled.Inventory2,
                title = "Aucun produit correspondant",
                description = if (isAdmin) "Créez votre premier produit ou modifiez les filtres actifs." else "Aucun produit ne correspond aux filtres choisis.",
                actionLabel = if (isAdmin) "Créer un produit" else null,
                onAction = if (isAdmin) {
                    { editingProduct = null; showProductDialog = true }
                } else null
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProducts, key = { it.id }) { p ->
                    val isRupture = p.actif && p.stockDisponible <= 0
                    val isLow = p.actif && p.stockDisponible in 1..p.seuilAlerte
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
                        border = BorderStroke(1.dp, border),
                        modifier = Modifier.testTag("product_card_${p.reference}")
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
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.nom, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(
                                        text = "${p.reference}${if (p.categorieNom != null) " • ${p.categorieNom}" else ""}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = TextSecondary
                                    )
                                }
                                StockStatusBadge(p.statut)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Prix unitaire", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(
                                        Formatters.formatMontant(p.prixVente, parametres.devise),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = TextPrimary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Stock dispo.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(
                                        "${p.stockDisponible}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isRupture) RuptureText else if (isLow) StockFaibleText else TextPrimary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Seuil alerte", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(
                                        "${p.seuilAlerte}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                        color = TextSecondary
                                    )
                                }
                            }

                            if (isAdmin) {
                                HorizontalDivider(color = AppOutlineSubtle)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { onToggleActive(p.id, !p.actif) },
                                        modifier = Modifier.testTag("toggle_archive_${p.reference}")
                                    ) {
                                        Icon(
                                            if (p.actif) Icons.Filled.Archive else Icons.Filled.Unarchive,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (p.actif) "Archiver" else "Réactiver")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            editingProduct = p
                                            showProductDialog = true
                                        },
                                        modifier = Modifier.testTag("edit_product_${p.reference}")
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Modifier")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProductDialog) {
        ProductFormDialog(
            editing = editingProduct,
            categories = categories,
            devise = parametres.devise,
            onDismiss = { showProductDialog = false },
            onSave = onSaveProduct
        )
    }

    if (showCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        var catErr by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Nouvelle catégorie") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it; catErr = null },
                        label = { Text("Nom de la catégorie *") },
                        isError = catErr != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("category_name_input")
                    )
                    if (!catErr.isNullOrBlank()) {
                        Text(catErr!!, color = RuptureText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.trim().isEmpty()) {
                            catErr = "Le nom de la catégorie est obligatoire."
                        } else {
                            onCreateCategory(catName) { ok ->
                                if (ok) showCategoryDialog = false else catErr = "Cette catégorie existe déjà."
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_category_button")
                ) {
                    Text("Créer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormDialog(
    editing: ProduitAvecStock?,
    categories: List<CategorieEntity>,
    devise: String,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, Long?, Long, Int, Int, (ServiceResult<*>) -> Unit) -> Unit
) {
    var reference by remember { mutableStateOf(editing?.reference ?: "") }
    var nom by remember { mutableStateOf(editing?.nom ?: "") }
    var selectedCatId by remember { mutableStateOf(editing?.categorieId) }
    var prixText by remember { mutableStateOf(editing?.prixVente?.toString() ?: "") }
    var seuilText by remember { mutableStateOf(editing?.seuilAlerte?.toString() ?: "5") }
    var stockInitText by remember { mutableStateOf("0") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var catMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Créer un produit" else "Modifier ${editing.reference}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it.uppercase(); errorMsg = null },
                    label = { Text("Référence unique *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_ref_input")
                )
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it; errorMsg = null },
                    label = { Text("Nom du produit *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                )

                ExposedDropdownMenuBox(
                    expanded = catMenuExpanded,
                    onExpandedChange = { catMenuExpanded = it }
                ) {
                    val catLabel = categories.firstOrNull { it.id == selectedCatId }?.nom ?: "Sans catégorie"
                    OutlinedTextField(
                        value = catLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Catégorie (facultative)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catMenuExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = catMenuExpanded,
                        onDismissRequest = { catMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sans catégorie") },
                            onClick = { selectedCatId = null; catMenuExpanded = false }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.nom) },
                                onClick = { selectedCatId = cat.id; catMenuExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = prixText,
                    onValueChange = { prixText = it.filter { ch -> ch.isDigit() }; errorMsg = null },
                    label = { Text("Prix de vente ($devise) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_price_input")
                )
                OutlinedTextField(
                    value = seuilText,
                    onValueChange = { seuilText = it.filter { ch -> ch.isDigit() }; errorMsg = null },
                    label = { Text("Seuil d'alerte (>= 0) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_threshold_input")
                )

                if (editing == null) {
                    OutlinedTextField(
                        value = stockInitText,
                        onValueChange = { stockInitText = it.filter { ch -> ch.isDigit() }; errorMsg = null },
                        label = { Text("Stock initial (mouvement initial)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("product_initial_stock_input")
                    )
                } else {
                    Text(
                        "Le stock (${editing.stockDisponible}) ne se modifie jamais depuis la fiche produit ; utilisez Réapprovisionnement ou Correction dans Stocks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = RuptureText, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prix = prixText.toLongOrNull() ?: 0L
                    val seuil = seuilText.toIntOrNull() ?: -1
                    val initStock = stockInitText.toIntOrNull() ?: 0
                    onSave(editing?.id, reference, nom, selectedCatId, prix, seuil, initStock) { res ->
                        if (res is ServiceResult.Succes) {
                            onDismiss()
                        } else if (res is ServiceResult.Erreur) {
                            errorMsg = res.message
                        }
                    }
                },
                modifier = Modifier.testTag("save_product_button")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
